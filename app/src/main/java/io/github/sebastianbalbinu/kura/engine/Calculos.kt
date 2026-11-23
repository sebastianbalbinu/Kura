package io.github.sebastianbalbinu.kura.engine

import io.github.sebastianbalbinu.kura.data.Cofre
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.Oficio
import io.github.sebastianbalbinu.kura.data.PagoFijo
import io.github.sebastianbalbinu.kura.data.TipoOficio
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Todas las cuentas de Kura. Las hace el código, nunca la IA:
 * así los números siempre son exactos.
 */
object Calculos {

    fun cofre(e: Estado, c: Cofre) = e.cofres[c] ?: 0L

    // ---------------- pagos fijos ----------------
    fun pagadosMes(e: Estado, d: LocalDate = hoy()) = e.pagados[mesClave(d)] ?: emptySet()

    fun fijosPendientesMes(e: Estado, d: LocalDate = hoy()): List<PagoFijo> {
        val pagados = pagadosMes(e, d)
        return e.pagosFijos.filter { it.id !in pagados }.sortedBy { it.dia }
    }

    fun totalPendienteMes(e: Estado) = fijosPendientesMes(e).sumOf { it.monto }

    /** Lo que todavía falta apartar para cubrir los pagos fijos del mes. */
    fun faltaFijos(e: Estado) = max(0L, totalPendienteMes(e) - cofre(e, Cofre.FIJOS))

    fun fechaDePago(dia: Int, mes: LocalDate): LocalDate =
        mes.withDayOfMonth(min(max(dia, 1), mes.lengthOfMonth()))

    // ---------------- reparto ----------------
    /** El organizador de Minecraft: reparte un ingreso entre los cofres. */
    fun repartir(e: Estado, monto: Long, oficio: Oficio?): Map<Cofre, Long> {
        val negocio = monto * (oficio?.pctNegocio ?: 0) / 100
        var resto = monto - negocio
        val tope = if (oficio?.tipo == TipoOficio.FIJO) resto else resto * 60 / 100
        val aFijos = min(faltaFijos(e), tope)
        resto -= aFijos
        val colchon = resto * e.ajustes.colchonPct / 100
        val libre = resto - colchon
        return linkedMapOf(Cofre.FIJOS to aFijos, Cofre.NEGOCIO to negocio, Cofre.COLCHON to colchon, Cofre.LIBRE to libre)
            .filterValues { it > 0 }
    }

    // ---------------- días ----------------
    fun diasRestantesMes(d: LocalDate = hoy()) = d.lengthOfMonth() - d.dayOfMonth + 1
    fun esDescanso(e: Estado, d: LocalDate) = d.toString() in e.ajustes.descansos
    fun diasTrabajoRestantes(e: Estado, d: LocalDate = hoy()) =
        (0 until diasRestantesMes(d)).count { !esDescanso(e, d.plusDays(it.toLong())) }

    // ---------------- plata segura ----------------
    /** Ingresos fijos (como la agencia) que llegan entre dos fechas y aún no han llegado. Neto de negocio. */
    fun segurosEntre(e: Estado, desde: LocalDate, hasta: LocalDate): Long {
        var total = 0L
        var mes = desde.withDayOfMonth(1)
        while (!mes.isAfter(hasta)) {
            e.oficios.filter { it.tipo == TipoOficio.FIJO && it.montoFijo > 0 && it.diaPago > 0 }.forEach { o ->
                val f = fechaDePago(o.diaPago, mes)
                val yaLlego = e.movimientos.any {
                    it.ingreso && it.oficioId == o.id && mesClave(fechaDe(it.fecha)) == mesClave(mes)
                }
                if (!f.isBefore(desde) && !f.isAfter(hasta) && !yaLlego) {
                    total += o.montoFijo - o.montoFijo * o.pctNegocio / 100
                }
            }
            mes = mes.plusMonths(1)
        }
        return total
    }

    /** Pagos fijos que vencen entre hoy y una fecha y no están pagados. */
    fun fijosHasta(e: Estado, hasta: LocalDate): Long {
        val d = hoy()
        var total = 0L
        var mes = d.withDayOfMonth(1)
        while (!mes.isAfter(hasta)) {
            val pagados = pagadosMes(e, mes)
            e.pagosFijos.forEach { p ->
                // incluye los de este mes ya vencidos y sin pagar
                if (p.id !in pagados && !fechaDePago(p.dia, mes).isAfter(hasta)) total += p.monto
            }
            mes = mes.plusMonths(1)
        }
        return total
    }

    // ---------------- meta del día ----------------
    data class Meta(
        val meta: Long,
        val lleva: Long,
        val descansa: Boolean,
        val horasFaltan: Double?,
        val oficioHoras: Oficio?,
        val diasTrabajo: Int
    )

    fun ingresosDelDia(e: Estado, d: LocalDate = hoy()) = e.movimientos
        .filter { it.ingreso && fechaDe(it.fecha) == d && e.oficioPor(it.oficioId)?.tipo != TipoOficio.FIJO }
        .sumOf { it.monto }

    fun metaDelDia(e: Estado): Meta {
        val d = hoy()
        val fin = d.withDayOfMonth(d.lengthOfMonth())
        val dias = diasRestantesMes(d)
        val libreDiario = e.ajustes.libreMensual / d.lengthOfMonth()
        val libreNecesario = max(0L, libreDiario * dias - cofre(e, Cofre.LIBRE))
        val seguros = segurosEntre(e, d, fin)
        val neto = max(0L, faltaFijos(e) + libreNecesario - seguros)
        val bruto = neto * 100 / max(1, 100 - e.ajustes.colchonPct)
        val lleva = ingresosDelDia(e, d)
        val descansa = esDescanso(e, d)
        val diasT = max(1, diasTrabajoRestantes(e, d))
        val meta = if (descansa) 0L else redondearArriba((bruto + lleva) / diasT)
        val oficioHoras = e.oficios.firstOrNull { it.tipo == TipoOficio.HORAS }
        val horas = oficioHoras?.let { o ->
            val t = tarifaReal(e, o)
            if (t > 0) max(0L, meta - lleva).toDouble() / t else null
        }
        return Meta(meta, lleva, descansa, horas, oficioHoras, diasT)
    }

    /** Cuánto puede gastar hoy con tranquilidad: lo libre repartido en lo que queda del mes. */
    fun puedeGastarHoy(e: Estado) = redondearAbajo(cofre(e, Cofre.LIBRE) / diasRestantesMes())

    // ---------------- tarifas que aprenden ----------------
    /** Mezcla lo que el usuario dijo con lo que de verdad ha registrado. Con 20 h registradas, manda lo real. */
    fun tarifaReal(e: Estado, o: Oficio): Long {
        val conHoras = e.movimientos.filter { it.ingreso && it.oficioId == o.id && it.horas > 0 }
        val horas = conHoras.sumOf { it.horas }
        if (horas <= 0) return o.tarifaHora
        val real = (conHoras.sumOf { it.monto } / horas).toLong()
        val peso = min(horas, 20.0) / 20.0
        return (o.tarifaHora * (1 - peso) + real * peso).toLong()
    }

    data class ResumenOficio(val oficio: Oficio, val ingresos: Long, val horas: Double, val porHora: Long, val veces: Int)

    fun resumenMes(e: Estado, d: LocalDate = hoy()): List<ResumenOficio> {
        val mes = mesClave(d)
        return e.oficios.map { o ->
            val movs = e.movimientos.filter { it.ingreso && it.oficioId == o.id && mesClave(fechaDe(it.fecha)) == mes }
            val horas = movs.sumOf { it.horas }
            ResumenOficio(o, movs.sumOf { it.monto }, horas,
                if (horas > 0) (movs.sumOf { it.monto } / horas).toLong() else tarifaReal(e, o), movs.size)
        }
    }

    // ---------------- ¿puedo pagar esto? ----------------
    data class Respuesta(
        val nivel: Int, // 0 tranquilo, 1 toca el colchón, 2 mejor no
        val desglose: List<Pair<String, String>>,
        val queda: Long,
        val sugerencia: LocalDate?
    )

    fun disponibleEn(e: Estado, fecha: LocalDate): Triple<Long, Long, Long> {
        val seguros = segurosEntre(e, hoy(), fecha)
        val falta = max(0L, fijosHasta(e, fecha) - cofre(e, Cofre.FIJOS))
        return Triple(cofre(e, Cofre.LIBRE) + seguros - falta, seguros, falta)
    }

    fun puedoPagar(e: Estado, monto: Long, fecha: LocalDate): Respuesta {
        val (disp, seguros, falta) = disponibleEn(e, fecha)
        val queda = disp - monto
        val desglose = buildList {
            add("Cofre libre hoy" to pesos(cofre(e, Cofre.LIBRE)))
            if (seguros > 0) add("Entra seguro antes del ${fecha.dayOfMonth}" to pesos(seguros))
            if (falta > 0) add("Falta para pagos fijos" to pesos(-falta))
            add("Queda después de pagarlo" to pesos(queda))
        }
        val nivel = when {
            queda >= 0 -> 0
            queda + cofre(e, Cofre.COLCHON) >= 0 -> 1
            else -> 2
        }
        val sugerencia = if (nivel == 0) null else (1..45L).map { fecha.plusDays(it) }
            .firstOrNull { disponibleEn(e, it).first >= monto }
        return Respuesta(nivel, desglose, queda, sugerencia)
    }
}

fun Estado.oficioPor(id: String?) = oficios.firstOrNull { it.id == id }
