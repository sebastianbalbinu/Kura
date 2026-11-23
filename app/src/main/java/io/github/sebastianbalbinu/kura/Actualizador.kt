package io.github.sebastianbalbinu.kura

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Version(val codigo: Long, val nombre: String, val url: String, val notas: String)

sealed class EstadoActualizacion {
    data object Nada : EstadoActualizacion()
    data class Disponible(val v: Version) : EstadoActualizacion()
    data class Descargando(val v: Version, val progreso: Float) : EstadoActualizacion()
    data class Lista(val v: Version, val archivo: File) : EstadoActualizacion()
    data class Error(val mensaje: String) : EstadoActualizacion()
}

/**
 * Busca versiones nuevas en los Releases de GitHub, las descarga y abre el instalador de Android.
 * Android siempre pide un toque de confirmación: fuera de la Play Store no se puede instalar en silencio.
 */
class Actualizador(private val context: Context) {
    private val api = "https://api.github.com/repos/sebastianbalbinu/Kura/releases/latest"

    private val _estado = MutableStateFlow<EstadoActualizacion>(EstadoActualizacion.Nada)
    val estado: StateFlow<EstadoActualizacion> = _estado

    val versionActual: Long get() = PackageInfoCompat.getLongVersionCode(
        context.packageManager.getPackageInfo(context.packageName, 0))
    val nombreActual: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Devuelve true si hay una versión más nueva. */
    suspend fun buscar(): Boolean = withContext(Dispatchers.IO) {
        try {
            val c = URL(api).openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.connectTimeout = 8000; c.readTimeout = 8000
            if (c.responseCode != 200) return@withContext false
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.getString("tag_name")                     // "v0.1.12"
            val codigo = tag.substringAfterLast('.').toLongOrNull() ?: return@withContext false
            val assets = j.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext false
            if (codigo > versionActual) {
                _estado.value = EstadoActualizacion.Disponible(
                    Version(codigo, tag.removePrefix("v"), apk.getString("browser_download_url"), j.optString("body")))
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun descargar(v: Version) {
        val carpeta = File(context.getExternalFilesDir(null) ?: context.filesDir, "actualizacion").apply { mkdirs() }
        carpeta.listFiles()?.forEach { it.delete() }
        val destino = File(carpeta, "Kura-${v.nombre}.apk")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(
            DownloadManager.Request(Uri.parse(v.url))
                .setTitle("Kura ${v.nombre}")
                .setDestinationUri(Uri.fromFile(destino))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        )
        _estado.value = EstadoActualizacion.Descargando(v, 0f)
        while (true) {
            delay(500)
            val c = dm.query(DownloadManager.Query().setFilterById(id)) ?: break
            c.use {
                if (!it.moveToFirst()) { _estado.value = EstadoActualizacion.Error("Se canceló la descarga."); return }
                val st = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val hecho = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _estado.value = EstadoActualizacion.Lista(v, destino); instalar(destino); return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        _estado.value = EstadoActualizacion.Error("No se pudo descargar. Revise la conexión."); return
                    }
                    else -> _estado.value = EstadoActualizacion.Descargando(v, if (total > 0) hecho.toFloat() / total else 0f)
                }
            }
        }
    }

    /** Abre el instalador de Android. Si falta el permiso, lleva a la pantalla para darlo. */
    fun instalar(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
