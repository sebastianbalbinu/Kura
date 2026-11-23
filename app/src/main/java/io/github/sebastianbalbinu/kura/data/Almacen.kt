package io.github.sebastianbalbinu.kura.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Guarda todo en un solo archivo JSON dentro del celular.
 * Nada sale del teléfono. Se escribe primero a un temporal para no corromper datos.
 */
class Almacen(context: Context) {
    private val archivo = File(context.filesDir, "kura.json")
    private val temporal = File(context.filesDir, "kura.json.tmp")

    fun cargar(): Estado = try {
        if (archivo.exists()) desdeJson(JSONObject(archivo.readText())) else Estado()
    } catch (e: Exception) {
        Estado()
    }

    @Synchronized
    fun guardar(estado: Estado) {
        temporal.writeText(aJson(estado).toString())
        if (!temporal.renameTo(archivo)) {
            archivo.writeText(temporal.readText()); temporal.delete()
        }
    }

    // ---------- a JSON ----------
    private fun aJson(e: Estado) = JSONObject().apply {
        put("version", 1)
        put("oficios", JSONArray(e.oficios.map { o ->
            JSONObject().put("id", o.id).put("nombre", o.nombre).put("emoji", o.emoji)
                .put("tipo", o.tipo.name).put("tarifaHora", o.tarifaHora).put("pctNegocio", o.pctNegocio)
                .put("palabras", JSONArray(o.palabras)).put("diaPago", o.diaPago).put("montoFijo", o.montoFijo)
        }))
        put("movimientos", JSONArray(e.movimientos.map { m ->
            JSONObject().put("id", m.id).put("fecha", m.fecha).put("monto", m.monto).put("ingreso", m.ingreso)
                .put("oficioId", m.oficioId ?: JSONObject.NULL).put("cofre", m.cofre?.name ?: JSONObject.NULL)
                .put("nota", m.nota).put("horas", m.horas)
                .put("reparto", JSONObject().apply { m.reparto.forEach { (c, v) -> put(c.name, v) } })
                .put("pagoFijoId", m.pagoFijoId ?: JSONObject.NULL)
        }))
        put("pagosFijos", JSONArray(e.pagosFijos.map { p ->
            JSONObject().put("id", p.id).put("nombre", p.nombre).put("monto", p.monto).put("dia", p.dia)
        }))
        put("pagados", JSONObject().apply { e.pagados.forEach { (mes, ids) -> put(mes, JSONArray(ids.toList())) } })
        put("cobros", JSONArray(e.cobros.map { c ->
            JSONObject().put("id", c.id).put("cliente", c.cliente).put("monto", c.monto).put("desde", c.desde)
                .put("oficioId", c.oficioId ?: JSONObject.NULL).put("pagado", c.pagado)
        }))
        put("cofres", JSONObject().apply { e.cofres.forEach { (c, v) -> put(c.name, v) } })
        put("ajustes", JSONObject().put("colchonPct", e.ajustes.colchonPct).put("libreMensual", e.ajustes.libreMensual)
            .put("descansos", JSONArray(e.ajustes.descansos.toList())).put("modeloIa", e.ajustes.modeloIa)
            .put("sonido", e.ajustes.sonido))
        // Solo los últimos 200 mensajes del chat
        put("chat", JSONArray(e.chat.takeLast(200).map { m ->
            JSONObject().put("id", m.id).put("deKenzo", m.deKenzo).put("texto", m.texto).put("fecha", m.fecha)
                .put("desglose", JSONArray(m.desglose.map { JSONArray(listOf(it.first, it.second)) }))
        }))
        put("bienvenidaVista", e.bienvenidaVista)
    }

    // ---------- desde JSON ----------
    private fun desdeJson(j: JSONObject): Estado {
        val base = Estado()
        val oficios = j.optJSONArray("oficios")?.objetos()?.map { o ->
            Oficio(
                id = o.getString("id"), nombre = o.getString("nombre"), emoji = o.optString("emoji", "•"),
                tipo = runCatching { TipoOficio.valueOf(o.getString("tipo")) }.getOrDefault(TipoOficio.PROYECTOS),
                tarifaHora = o.optLong("tarifaHora"), pctNegocio = o.optInt("pctNegocio"),
                palabras = o.optJSONArray("palabras")?.textos() ?: emptyList(),
                diaPago = o.optInt("diaPago"), montoFijo = o.optLong("montoFijo")
            )
        } ?: base.oficios
        val movimientos = j.optJSONArray("movimientos")?.objetos()?.map { m ->
            Movimiento(
                id = m.getLong("id"), fecha = m.getLong("fecha"), monto = m.getLong("monto"),
                ingreso = m.getBoolean("ingreso"), oficioId = m.textoONulo("oficioId"),
                cofre = m.textoONulo("cofre")?.let { runCatching { Cofre.valueOf(it) }.getOrNull() },
                nota = m.optString("nota"), horas = m.optDouble("horas", 0.0),
                reparto = m.optJSONObject("reparto")?.let { r ->
                    Cofre.entries.filter { r.has(it.name) }.associateWith { r.getLong(it.name) }
                } ?: emptyMap(),
                pagoFijoId = if (m.isNull("pagoFijoId")) null else m.optLong("pagoFijoId")
            )
        } ?: emptyList()
        val pagosFijos = j.optJSONArray("pagosFijos")?.objetos()?.map {
            PagoFijo(it.getLong("id"), it.getString("nombre"), it.getLong("monto"), it.getInt("dia"))
        } ?: emptyList()
        val pagados = j.optJSONObject("pagados")?.let { p ->
            p.keys().asSequence().associateWith { k ->
                val a = p.getJSONArray(k); (0 until a.length()).map { a.getLong(it) }.toSet()
            }
        } ?: emptyMap()
        val cobros = j.optJSONArray("cobros")?.objetos()?.map {
            Cobro(it.getLong("id"), it.getString("cliente"), it.getLong("monto"), it.getLong("desde"),
                it.textoONulo("oficioId"), it.optBoolean("pagado"))
        } ?: emptyList()
        val cofres = j.optJSONObject("cofres")?.let { c ->
            Cofre.entries.associateWith { c.optLong(it.name, 0L) }
        } ?: base.cofres
        val aj = j.optJSONObject("ajustes")
        val ajustes = if (aj == null) Ajustes() else Ajustes(
            colchonPct = aj.optInt("colchonPct", 10), libreMensual = aj.optLong("libreMensual", 600_000),
            descansos = aj.optJSONArray("descansos")?.textos()?.toSet() ?: emptySet(),
            modeloIa = aj.optString("modeloIa", "auto"), sonido = aj.optBoolean("sonido", true)
        )
        val chat = j.optJSONArray("chat")?.objetos()?.map { m ->
            MensajeChat(
                m.getLong("id"), m.getBoolean("deKenzo"), m.getString("texto"), m.getLong("fecha"),
                m.optJSONArray("desglose")?.let { d ->
                    (0 until d.length()).map { i -> d.getJSONArray(i).let { it.getString(0) to it.getString(1) } }
                } ?: emptyList()
            )
        } ?: emptyList()
        return Estado(oficios, movimientos, pagosFijos, pagados, cobros, cofres, ajustes, chat,
            j.optBoolean("bienvenidaVista", false))
    }

    private fun JSONArray.objetos() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.textos() = (0 until length()).map { getString(it) }
    private fun JSONObject.textoONulo(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)
}
