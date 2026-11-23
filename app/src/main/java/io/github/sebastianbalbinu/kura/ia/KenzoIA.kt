package io.github.sebastianbalbinu.kura.ia

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
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
    data class Descargando(val modelo: Modelo, val progreso: Float) : EstadoIA()
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
    private var descargaId: Long = -1

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

    /** Descarga el modelo solo por wifi y luego lo carga. */
    suspend fun descargarYCargar(m: Modelo) {
        if (descargado(m)) { cargar(m); return }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val tmp = File(carpeta, m.archivo + ".parcial"); tmp.delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("Kenzō · ${m.nombre}")
            .setDescription("El cerebro de Kenzō (${m.tamanoMb} MB)")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setAllowedOverMetered(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(tmp))
        descargaId = dm.enqueue(req)
        _estado.value = EstadoIA.Descargando(m, 0f)
        while (true) {
            delay(800)
            val c = dm.query(DownloadManager.Query().setFilterById(descargaId)) ?: break
            c.use {
                if (!it.moveToFirst()) { _estado.value = EstadoIA.Error("La descarga se canceló."); return }
                val st = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val hecho = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        tmp.renameTo(archivo(m)); cargar(m); return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        _estado.value = EstadoIA.Error("No se pudo descargar. Revise el wifi e intente de nuevo."); return
                    }
                    else -> _estado.value = EstadoIA.Descargando(m, if (total > 0) hecho.toFloat() / total else 0f)
                }
            }
        }
    }

    fun cancelarDescarga() {
        if (descargaId >= 0) {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(descargaId)
            descargaId = -1
        }
        _estado.value = EstadoIA.SinModelo
    }

    fun borrar(m: Modelo) {
        apagar(); archivo(m).delete(); _estado.value = EstadoIA.SinModelo
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
