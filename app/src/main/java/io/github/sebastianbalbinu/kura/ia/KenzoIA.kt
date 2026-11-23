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

    private var engine: Engine? = null
    private val candado = Mutex()

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
        engine?.let { runCatching { it.close() } }
        engine = null
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

    suspend fun cargar(m: Modelo) = withContext(Dispatchers.Default) {
        candado.withLock {
            if (!descargado(m)) { _estado.value = EstadoIA.SinModelo; return@withLock }
            engine?.let { runCatching { it.close() } }; engine = null
            _estado.value = EstadoIA.Cargando(m)
            try {
                val e = Engine(EngineConfig(modelPath = archivo(m).absolutePath, backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path))
                e.initialize()
                engine = e
                _estado.value = EstadoIA.Lista(m, null)
            } catch (t: Throwable) {
                _estado.value = EstadoIA.Error("No pude cargar el modelo: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    val lista get() = engine != null && _estado.value is EstadoIA.Lista

    /** Le pregunta algo a la IA. Cada pregunta es una conversación nueva y corta, para ir rápido. */
    suspend fun preguntar(sistema: String, mensaje: String, temperatura: Double = 0.4): String? =
        withContext(Dispatchers.Default) {
            candado.withLock {
                val e = engine ?: return@withLock null
                try {
                    val cfg = ConversationConfig(
                        systemInstruction = Contents.of(sistema),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.9, temperature = temperatura)
                    )
                    e.createConversation(cfg).use { conv ->
                        limpiar(conv.sendMessage(mensaje).toString())
                    }
                } catch (t: Throwable) {
                    null
                }
            }
        }

    /** Mide cuántas letras por segundo escribe el modelo en este teléfono. */
    suspend fun medirVelocidad(): Float? {
        val ini = System.nanoTime()
        val r = preguntar("Responde en español, breve.", "Escribe una frase corta sobre ahorrar dinero.") ?: return null
        val seg = (System.nanoTime() - ini) / 1e9f
        val v = r.length / seg
        (estado.value as? EstadoIA.Lista)?.let { _estado.value = it.copy(velocidad = v) }
        return v
    }

    private fun limpiar(s: String) = s
        .replace(Regex("(?s)<think>.*?</think>"), "")
        .replace(Regex("(?s)^.*</think>"), "")
        .trim()
}
