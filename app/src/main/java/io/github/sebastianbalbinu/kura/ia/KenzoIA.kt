package io.github.sebastianbalbinu.kura.ia

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Los cerebros posibles de Kenzō, del más liviano al más listo. */
enum class Modelo(val clave: String, val nombre: String, val archivo: String, val url: String, val tamanoMb: Int) {
    Q08B("q08b", "Qwen 3.5 · 0.8B", "Qwen3.5-0.8B_int8.litertlm",
        "https://huggingface.co/litert-community/Qwen3.5-0.8B/resolve/main/Qwen3.5-0.8B_int8.litertlm", 965),
    Q2B("q2b", "Qwen 3.5 · 2B", "Qwen3.5-2B_int8.litertlm",
        "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/main/Qwen3.5-2B_int8.litertlm", 2120);

    companion object {
        fun porClave(c: String) = entries.firstOrNull { it.clave == c }
    }
}

sealed class EstadoIA {
    data object SinModelo : EstadoIA()
    data class Descargando(val modelo: Modelo, val progreso: Float, val reintentando: Boolean = false) : EstadoIA()
    data class Cargando(val modelo: Modelo) : EstadoIA()
    data class Lista(val modelo: Modelo, val velocidad: Float?) : EstadoIA()
    data class Error(val mensaje: String) : EstadoIA()
    data object Apagada : EstadoIA()
}

/**
 * La IA de Kenzō, corriendo dentro del celular. Nada sale del teléfono.
 * Elige el modelo según la RAM, lo descarga solo por wifi y lo carga en segundo plano.
 */
class KenzoIA(private val context: Context) {

    private val _estado = MutableStateFlow<EstadoIA>(EstadoIA.SinModelo)
    val estado: StateFlow<EstadoIA> = _estado


    private val carpeta get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "modelos").apply { mkdirs() }
    fun archivo(m: Modelo) = File(carpeta, m.archivo)

    /** RAM total del teléfono, en GB. */
    fun ramGb(): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
        return info.totalMem / 1_073_741_824.0
    }

    /** Qué modelo le conviene a este teléfono. null = mejor sin IA. */
    fun recomendado(): Modelo? = when {
        ramGb() < 3.5 -> null
        ramGb() < 6.5 -> Modelo.Q08B
        else -> Modelo.Q2B
    }

    fun elegido(clave: String): Modelo? = when (clave) {
        "ninguno" -> null
        "auto" -> recomendado()
        else -> Modelo.porClave(clave)
    }

    fun descargado(m: Modelo) = archivo(m).let { it.exists() && it.length() > 50_000_000 }

    fun apagar() {
        modeloActivo = null
        _estado.value = EstadoIA.Apagada
    }

    @Volatile private var pausar = false

    private fun parcial(m: Modelo) = File(carpeta, m.archivo + ".parcial")

    /** Cuánto lleva descargado (0 a 1) si quedó una descarga a medias. */
    fun progresoGuardado(m: Modelo): Float =
        parcial(m).let { if (it.exists()) (it.length() / (m.tamanoMb * 1_000_000f)).coerceIn(0f, 0.99f) else 0f }

    private fun enWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /**
     * Descarga el modelo por partes, solo con wifi. Si se corta, reintenta solo y
     * continúa desde donde iba: nunca empieza de cero. Luego lo carga.
     */
    suspend fun descargarYCargar(m: Modelo) {
        if (descargado(m)) { cargar(m); return }
        pausar = false
        var fallos = 0
        while (true) {
            if (pausar) { _estado.value = EstadoIA.SinModelo; return }
            if (!enWifi()) {
                _estado.value = EstadoIA.Error("Conéctese a wifi para continuar. Va en ${(progresoGuardado(m) * 100).toInt()}%; no se pierde lo descargado.")
                return
            }
            try {
                if (bajarTramo(m)) {
                    parcial(m).renameTo(archivo(m))
                    cargar(m)
                    return
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                fallos++
                if (fallos > 10) {
                    _estado.value = EstadoIA.Error("La conexión se cortó varias veces. Toque Descargar para continuar desde ${(progresoGuardado(m) * 100).toInt()}%.")
                    return
                }
                _estado.value = EstadoIA.Descargando(m, progresoGuardado(m), reintentando = true)
                delay(2000L * fallos)
            }
        }
    }

    /** Baja lo que falta. Devuelve true cuando el archivo está completo. */
    private suspend fun bajarTramo(m: Modelo): Boolean = withContext(Dispatchers.IO) {
        val tmp = parcial(m)
        val desde = if (tmp.exists()) tmp.length() else 0L
        val c = URL(m.url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20_000
        c.readTimeout = 45_000
        if (desde > 0) c.setRequestProperty("Range", "bytes=$desde-")
        try {
            val codigo = c.responseCode
            val total: Long
            val seguir: Boolean
            when (codigo) {
                206 -> {
                    total = c.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                        ?: (desde + c.contentLengthLong)
                    seguir = true
                }
                200 -> { total = c.contentLengthLong; seguir = false }
                416 -> return@withContext desde > 0   // ya estaba completo
                else -> throw IOException("HTTP $codigo")
            }
            var hecho = if (seguir) desde else 0L
            var aviso = 0L
            FileOutputStream(tmp, seguir).use { out ->
                c.inputStream.use { inp ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (pausar) throw IOException("pausa")
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        hecho += n
                        if (hecho - aviso > 4_000_000) {
                            aviso = hecho
                            _estado.value = EstadoIA.Descargando(m, if (total > 0) hecho.toFloat() / total else 0f)
                        }
                    }
                }
            }
            if (total > 0 && tmp.length() < total) throw IOException("Llegó incompleto")
            true
        } finally {
            c.disconnect()
        }
    }

    /** Pausa la descarga. Lo descargado se guarda para continuar después. */
    fun pausarDescarga() {
        pausar = true
        _estado.value = EstadoIA.SinModelo
    }

    fun borrar(m: Modelo) {
        apagar(); archivo(m).delete(); parcial(m).delete(); _estado.value = EstadoIA.SinModelo
    }

    // ---------------------------------------------------------------- usar la IA
    // La guía del modelo pide un motor (Engine) nuevo por cada conversación,
    // así que Kenzō lo crea al preguntar y lo cierra al terminar. Así además
    // no se queda ocupando 2 GB de memoria mientras usted usa otras apps.

    private var modeloActivo: Modelo? = null
    private val ocupado = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Deja el modelo listo para usarse (sin cargarlo todavía en memoria). */
    fun cargar(m: Modelo) {
        if (!descargado(m)) { _estado.value = EstadoIA.SinModelo; return }
        modeloActivo = m
        _estado.value = EstadoIA.Lista(m, null)
    }

    val lista get() = modeloActivo != null && _estado.value !is EstadoIA.Descargando
    val pensandoAhora get() = ocupado.get()

    /**
     * Crea el motor, pregunta y lo cierra. Corre en un hilo aparte con tiempo límite:
     * si el teléfono se queda pegado, Kenzō no se congela con él.
     */
    suspend fun preguntar(sistema: String, mensaje: String, temperatura: Double = 0.4,
                          limiteSeg: Long = 240): String? {
        val m = modeloActivo ?: return null
        if (!ocupado.compareAndSet(false, true)) return null
        val resultado = kotlinx.coroutines.CompletableDeferred<Result<String>>()
        Thread({
            var e: Engine? = null
            val r = runCatching {
                e = Engine(EngineConfig(modelPath = archivo(m).absolutePath, backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path))
                e!!.initialize()
                val cfg = ConversationConfig(
                    systemInstruction = Contents.of(sistema),
                    samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = temperatura)
                )
                e!!.createConversation(cfg).use { conv -> limpiar(conv.sendMessage(mensaje).toString()) }
            }
            runCatching { e?.close() }
            ocupado.set(false)
            resultado.complete(r)
        }, "kenzo-ia").apply { priority = Thread.NORM_PRIORITY }.start()

        val r = kotlinx.coroutines.withTimeoutOrNull(limiteSeg * 1000) { resultado.await() }
        return when {
            r == null -> {
                _estado.value = EstadoIA.Error("La IA tardó más de $limiteSeg segundos en responder. Este teléfono puede necesitar el modelo más pequeño.")
                null
            }
            r.isFailure -> {
                val t = r.exceptionOrNull()
                _estado.value = EstadoIA.Error("La IA falló: ${t?.message ?: t?.javaClass?.simpleName}")
                null
            }
            else -> r.getOrNull()
        }
    }

    data class Prueba(val respuesta: String?, val segundos: Float)

    /** Le hace una pregunta corta para comprobar que funciona y cuánto tarda. */
    suspend fun probar(): Prueba {
        val m = modeloActivo ?: return Prueba(null, 0f)
        _estado.value = EstadoIA.Cargando(m)
        val ini = System.nanoTime()
        val r = preguntar("Responde en español, en una sola frase corta.", "Saluda a tu señor.", limiteSeg = 300)
        val seg = (System.nanoTime() - ini) / 1e9f
        if (r != null) _estado.value = EstadoIA.Lista(m, r.length / seg)
        return Prueba(r, seg)
    }

    private fun limpiar(s: String) = s
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace(Regex("(?s)^.*</think>"), "")
        .trim()
}
