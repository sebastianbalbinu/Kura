package io.github.sebastianbalbinu.kura.engine

import io.github.sebastianbalbinu.kura.data.Cofre
import io.github.sebastianbalbinu.kura.data.Estado
import java.time.DayOfWeek
import java.time.LocalDate

/** Lo que Kenzō entendió de una frase. */
sealed class Intencion {
    data class Ingreso(val monto: Long, val oficioId: String?, val nota: String, val horas: Double = 0.0) : Intencion()
    data class Gasto(val monto: Long, val oficioId: String?, val cofre: Cofre, val nota: String) : Intencion()
    data class PagoDeFijo(val pagoId: Long, val monto: Long) : Intencion()
    data class NuevoCobro(val cliente: String, val monto: Long, val oficioId: String?) : Intencion()
    data class CobroPagado(val cobroId: Long, val monto: Long?) : Intencion()
    data class Pregunta(val monto: Long, val fecha: LocalDate) : Intencion()
    data class Descanso(val fechas: List<LocalDate>) : Intencion()
    data class Tarifa(val oficioId: String, val tarifa: Long) : Intencion()
    data class NuevoPagoFijo(val nombre: String, val monto: Long, val dia: Int) : Intencion()
    data class SueldoFijo(val oficioId: String, val monto: Long, val dia: Int) : Intencion()
    data object Resumen : Intencion()
    data class Desconocido(val texto: String) : Intencion()
}

/**
 * Entiende frases como "me pagó Finelio 450 y eché 15 de gasolina" sin IA.
 * Lo que no entienda se lo pasa a la IA (si hay) o pregunta.
 */
object Interprete {

    private val ING = listOf("me pago", "me pagaron", "pagaron", "me entro", "me entraron", "entraron", "entro ",
        "cobre", "vendi", "gane", "me hice", "hice ", "recibi", "consignaron", "transfirieron", "giraron",
        "me llego", "me llegaron", "llegaron", "ingreso", "ingresaron", " pago ")
    private val GAS = listOf("pague", "compre", "gaste", "eche", "tanquee", "gasolina", "almuerzo", "almorce",
        "pedi", "invite", "me cobraron", "saque", "gasto", "mercado", "arregle", "domicilio")
    private val NEG = listOf("bolsa", "etiqueta", "imprenta", "impresion", "insumo", "cafe verde", "tostion", "trilla",
        "hosting", "dominio", "gasolina", "aceite", "llanta", "repuesto", "vinilo", "tinta", "material")
    private val PREG = listOf("puedo", "podemos", "podria", "alcanza", "me da para", "nos da para", "es posible")
    private val RES = listOf("como voy", "como vamos", "cuanto llevo", "cuanto tengo", "resumen", "como estoy", "como va")
    private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
        "septiembre", "octubre", "noviembre", "diciembre")
    private val DIAS = mapOf("lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY, "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY)

    private val NUM = Regex("""(\$\s*)?(\d{1,3}(?:\.\d{3})+|\d+(?:[.,]\d{1,2})?)\s*(millones|millon|palos|palo|mil|lucas|luca|k)?(?![\w])""")

    fun entender(texto: String, e: Estado): List<Intencion> {
        val t = " " + normal(texto).replace(Regex("[¿?¡!]"), " ").replace(Regex("\\s+"), " ").trim() + " "
        if (t.isBlank()) return emptyList()

        // Preguntas y descansos se miran sobre toda la frase.
        if (PREG.any { t.contains(it) }) {
            val m = montos(t).firstOrNull()
            if (m != null) return listOf(Intencion.Pregunta(m, fecha(t) ?: hoy()))
        }
        if (t.contains("descans") || t.contains("no trabajo") || t.contains("libre el")) {
            val fs = fechas(t)
            return listOf(Intencion.Descanso(fs.ifEmpty { listOf(hoy()) }))
        }
        if (RES.any { t.contains(it) } && montos(t).isEmpty()) return listOf(Intencion.Resumen)

        // Tarifas: "en uber me hago 18 mil la hora"
        if (t.contains("la hora") || t.contains("por hora") || t.contains("cada hora")) {
            val m = montos(t).firstOrNull()
            val o = oficio(t, e)
            if (m != null && o != null) return listOf(Intencion.Tarifa(o, m))
        }

        // "la agencia me paga 1.600.000 el 30"
        val dia = Regex("""(?:el |dia |cada )(\d{1,2})\b""").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val m0 = montos(t).firstOrNull()
        if (dia != null && m0 != null && dia in 1..31) {
            val of = oficio(t, e)
            val ofFijo = e.oficios.firstOrNull { it.id == of && it.tipo == io.github.sebastianbalbinu.kura.data.TipoOficio.FIJO }
            if (ofFijo != null && (t.contains(" me paga ") || t.contains(" pagan ") || t.contains(" me pagan "))) {
                return listOf(Intencion.SueldoFijo(ofFijo.id, m0, dia))
            }
            // "el arriendo es 800 mil el dia 1" / "internet cuesta 90 mil cada 15"
            val marca = listOf(" es ", " son ", " cuesta ", " vale ", " me cuesta ", " sale ", " pago mensual ")
                .firstOrNull { t.contains(it) }
            if (marca != null || t.contains("cada mes") || t.contains("mensual")) {
                val corte = marca?.let { t.indexOf(it) } ?: NUM.find(t)?.range?.first ?: 0
                var nombre = t.substring(0, corte).trim()
                listOf("el ", "la ", "mi ", "los ", "las ", "pago de ", "pago del ", "pago ", "lo del ", "lo de ")
                    .forEach { nombre = nombre.removePrefix(it) }
                if (nombre.isNotBlank() && nombre.length <= 40) {
                    return listOf(Intencion.NuevoPagoFijo(titulo(nombre.trim()), m0, dia))
                }
            }
        }

        // Varias cosas en una frase: "me pagaron 200 y eché 15 de gasolina"
        val partes = t.split(Regex(" y | tambien |, |; | ademas ")).map { " ${it.trim()} " }.filter { it.isNotBlank() }
        val res = mutableListOf<Intencion>()
        var ultimoTipoIngreso: Boolean? = null
        for (p in partes) {
            val m = montos(p).firstOrNull()
            val i = clausula(p, m, e, ultimoTipoIngreso)
            if (i != null) {
                res += i
                ultimoTipoIngreso = when (i) {
                    is Intencion.Ingreso, is Intencion.CobroPagado -> true
                    is Intencion.Gasto, is Intencion.PagoDeFijo -> false
                    else -> ultimoTipoIngreso
                }
            }
        }
        return res.ifEmpty { listOf(Intencion.Desconocido(texto)) }
    }

    private fun clausula(p: String, m: Long?, e: Estado, previo: Boolean?): Intencion? {
        val nota = p.trim().replaceFirstChar { it.uppercase() }

        // "La Casa del Pan me debe 300"
        val iDebe = p.indexOf(" me debe")
        if (iDebe >= 0 && m != null) {
            val cliente = p.substring(0, iDebe).trim().removePrefix("el ").removePrefix("la ").trim()
            if (cliente.isNotEmpty()) return Intencion.NuevoCobro(titulo(cliente), m, oficio(p, e))
        }

        val esIng = ING.any { p.contains(it) }
        val esGas = GAS.any { p.contains(it) }

        // ¿Pagó alguien que debía?
        if (esIng || p.contains(" ya pago") || p.contains("ya me pago")) {
            e.cobros.filter { !it.pagado }.firstOrNull { p.contains(normal(it.cliente)) }?.let { c ->
                return Intencion.CobroPagado(c.id, m)
            }
        }

        // ¿Es un pago fijo? "pagué el arriendo"
        if (esGas || (p.contains("pago") && !p.contains("me pago") && !p.contains("pagaron"))) {
            e.pagosFijos.firstOrNull { pf -> normal(pf.nombre).split(" ").any { w -> w.length > 3 && p.contains(w) } }
                ?.let { pf -> return Intencion.PagoDeFijo(pf.id, m ?: pf.monto) }
        }

        if (m == null) return null
        val of = oficio(p, e)
        val horas = Regex("""(\d+(?:,\d+)?)\s*(horas|hora|h)\b""").find(p)?.groupValues?.get(1)
            ?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

        val ingreso = when {
            esIng && !esGas -> true
            esGas && !esIng -> false
            esIng && esGas -> p.indexOf(ING.first { p.contains(it) }) < p.indexOf(GAS.first { p.contains(it) })
            NEG.any { p.contains(it) } -> false
            of != null && previo == null -> true      // "uber 85" → ingreso de Uber
            else -> previo ?: return Intencion.Desconocido(nota)
        }
        return if (ingreso) Intencion.Ingreso(m, of, nota, horas)
        else Intencion.Gasto(m, of, if (NEG.any { p.contains(it) }) Cofre.NEGOCIO else Cofre.LIBRE, nota)
    }

    fun oficio(t: String, e: Estado): String? {
        e.oficios.firstOrNull { o ->
            o.palabras.any { w -> t.contains(" ${normal(w)}") } ||
                normal(o.nombre).split(" ").any { w -> w.length > 3 && t.contains(w) }
        }?.let { return it.id }
        // Clientes conocidos: "me pagó Finelio" → el oficio de la última vez
        e.cobros.lastOrNull { t.contains(normal(it.cliente)) && it.oficioId != null }?.let { return it.oficioId }
        e.movimientos.lastOrNull { it.ingreso && it.oficioId != null && primeraPalabraClave(it.nota)?.let { k -> t.contains(k) } == true }
            ?.let { return it.oficioId }
        return null
    }

    private fun primeraPalabraClave(nota: String): String? {
        val vacias = setOf("me", "pago", "pagaron", "pagó", "entro", "de", "la", "el", "los", "las", "mil", "por", "ya")
        return normal(nota).split(" ").firstOrNull { it.length > 3 && it !in vacias && it.none(Char::isDigit) && it !in ING.map(String::trim) }
    }

    /** Todos los montos de una frase, en pesos. "450" → 450.000; "1,2 millones" → 1.200.000 */
    fun montos(t: String): List<Long> = NUM.findAll(t).mapNotNull { r ->
        val antes = t.substring(0, r.range.first)
        val despues = t.substring(r.range.last + 1).trimStart()
        val unidad = r.groupValues[3]
        if (unidad.isEmpty() && (despues.startsWith("hora") || despues.startsWith("h ") ||
                despues.startsWith("dia") || MESES.any { despues.startsWith("de $it") } || despues.startsWith("%"))
        ) return@mapNotNull null
        if (unidad.isEmpty() && r.groupValues[1].isEmpty() &&
            (antes.endsWith("el ") || antes.endsWith("dia ") || antes.endsWith("los ") || antes.endsWith("cada "))
        ) return@mapNotNull null
        val crudo = r.groupValues[2]
        val miles = Regex("""\d{1,3}(\.\d{3})+""").matches(crudo)
        val v = if (miles) crudo.replace(".", "").toDouble() else crudo.replace(',', '.').toDouble()
        val pesos = when (unidad) {
            "millones", "millon", "palos", "palo" -> v * 1_000_000
            "mil", "lucas", "luca", "k" -> v * 1000
            else -> if (v < 10_000 && !miles) v * 1000 else v
        }
        pesos.toLong().takeIf { it > 0 }
    }.toList()

    fun fecha(t: String): LocalDate? = fechas(t).firstOrNull()

    fun fechas(t: String): List<LocalDate> {
        val h = hoy()
        val res = mutableListOf<LocalDate>()
        if (t.contains("pasado manana")) res += h.plusDays(2)
        else if (t.contains("manana")) res += h.plusDays(1)
        if (Regex("\\bhoy\\b").containsMatchIn(t)) res += h
        if (t.contains("fin de mes")) res += h.withDayOfMonth(h.lengthOfMonth())
        DIAS.forEach { (nombre, dow) ->
            if (Regex("\\b$nombre\\b").containsMatchIn(t)) {
                var d = h.plusDays(1); while (d.dayOfWeek != dow) d = d.plusDays(1); res += d
            }
        }
        Regex("""(?:el |dia )(\d{1,2})(?: de (\w+))?""").findAll(t).forEach { r ->
            val dia = r.groupValues[1].toInt()
            val mesIdx = MESES.indexOf(r.groupValues[2])
            if (dia in 1..31) {
                var base = if (mesIdx >= 0) h.withMonth(mesIdx + 1).withDayOfMonth(1) else h.withDayOfMonth(1)
                if (mesIdx >= 0 && base.isBefore(h.withDayOfMonth(1))) base = base.plusYears(1)
                var d = base.withDayOfMonth(minOf(dia, base.lengthOfMonth()))
                if (mesIdx < 0 && d.isBefore(h)) d = h.plusMonths(1).withDayOfMonth(minOf(dia, h.plusMonths(1).lengthOfMonth()))
                res += d
            }
        }
        return res.distinct().sorted()
    }

    private fun titulo(s: String) = s.split(" ").joinToString(" ") { w ->
        if (w in setOf("de", "del", "la", "el", "y", "los", "las")) w else w.replaceFirstChar { it.uppercase() }
    }
}
