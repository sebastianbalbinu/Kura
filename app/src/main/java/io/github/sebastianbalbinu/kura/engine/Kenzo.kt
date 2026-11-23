package io.github.sebastianbalbinu.kura.engine

import io.github.sebastianbalbinu.kura.data.Cofre
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.TipoOficio
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** La voz de Kenzō sin IA: frases escritas a mano, tranquilas y respetuosas. */
object Kenzo {

    data class Proverbio(val es: String)

    val proverbios = listOf(
        Proverbio("Hasta el polvo, acumulado, se vuelve montaña."),
        Proverbio("Si tiene prisa, tome el camino largo."),
        Proverbio("Tres años sobre la piedra, y la piedra se calienta."),
        Proverbio("Quien compra lo barato pierde su dinero."),
        Proverbio("Coma hasta quedar ocho partes lleno."),
        Proverbio("Caer siete veces, levantarse ocho."),
        Proverbio("Quien está preparado no tiene de qué preocuparse."),
        Proverbio("La constancia es fuerza."),
        Proverbio("El tiempo es oro."),
        Proverbio("Madrugar vale tres monedas."),
        Proverbio("Un viaje de mil leguas empieza con un paso."),
        Proverbio("El dinero da vueltas por el mundo."),
        Proverbio("Saber que se tiene suficiente."),
        Proverbio("La gota de lluvia perfora la piedra."),
        Proverbio("Lo que no se sabe no inquieta… pero en Kura sí se sabe.")
    )

    fun proverbioDelDia() = proverbios[hoy().dayOfYear % proverbios.size]

    fun saludo(): String = when (LocalTime.now(ZONA).hour) {
        in 5..11 -> "Buenos días, señor."
        in 12..18 -> "Buenas tardes, señor."
        else -> "Buenas noches, señor."
    }

    private fun <T> una(vararg op: T): T = op.random()

    /** Lo que Kenzō le dice en el inicio. Prioriza lo urgente, sin alarmar. */
    fun aviso(e: Estado): String {
        val h = hoy()
        if (!e.bienvenidaVista || (e.pagosFijos.isEmpty() && e.movimientos.isEmpty()))
            return "Bienvenido a su kura, señor. Cuénteme qué paga cada mes, por ejemplo: «el arriendo es 800 mil el día 1». Yo me encargo del resto."

        val falta = Calculos.faltaFijos(e)
        Calculos.fijosPendientesMes(e).firstOrNull()?.let { p ->
            val f = Calculos.fechaDePago(p.dia, h)
            val dias = ChronoUnit.DAYS.between(h, f)
            if (dias in 0..5 && falta > 0)
                return "${p.nombre} se paga el ${f.dayOfMonth}. Faltan ${pesos(falta)} en el cofre de pagos fijos; le sugiero no tocar lo libre estos días."
            if (dias < 0)
                return "${p.nombre} venció el ${f.dayOfMonth} y no lo veo pagado. Si ya lo pagó, dígame «pagué ${p.nombre.lowercase()}»."
        }
        e.cobros.filter { !it.pagado }.maxByOrNull { h.toEpochDay() - fechaDe(it.desde).toEpochDay() }?.let { c ->
            val dias = ChronoUnit.DAYS.between(fechaDe(c.desde), h)
            if (dias >= 7) return "${c.cliente} le debe ${pesos(c.monto)} desde hace $dias días. Buen momento para recordarle con amabilidad."
        }
        e.oficios.firstOrNull { it.tipo == TipoOficio.FIJO && it.montoFijo <= 0 }?.let { o ->
            return "Para calcular mejor, señor: ¿cuánto le paga ${o.nombre} y qué día? Puede ajustarlo en Oficios."
        }
        if (e.pagosFijos.isEmpty())
            return "Aún no conozco sus pagos fijos. Dígame por ejemplo «el internet es 90 mil el 15» y los iré apartando."
        val meta = Calculos.metaDelDia(e)
        if (meta.descansa) return una("Hoy descansa, señor. Ya lo tuve en cuenta en las cuentas.", "Día de descanso. La bodega está cuidada.")
        if (meta.meta > 0 && meta.lleva >= meta.meta)
            return una("Meta cumplida, señor. Lo que haga de aquí en adelante es extra.", "Ya llegó a la meta de hoy. Puede descansar con tranquilidad.")
        if (falta == 0L && e.pagosFijos.isNotEmpty())
            return una("Los pagos fijos del mes ya están apartados. Todo en orden.", "Los cofres están en orden, señor.")
        return una("Aquí estoy, señor. Cuénteme cada movimiento y yo lo organizo.", "Todo tranquilo en la bodega.")
    }

    fun desgloseReparto(r: Map<Cofre, Long>) = r.map { (c, v) -> "${c.icono} ${c.nombre}" to pesos(v) }

    fun recibido(monto: Long, oficio: String?) =
        una("Recibido, señor.", "Muy bien, señor.", "Anotado, señor.") +
            " ${pesos(monto)}${oficio?.let { " de $it" } ?: ""}. Así lo repartí:"

    fun ayuda() = "Disculpe, señor, no entendí bien. Puede decirme cosas como:\n" +
        "• «me pagaron 200 de la web»\n• «eché 15 de gasolina»\n• «la Casa del Pan me debe 300»\n" +
        "• «¿puedo pagar 150 mil el 5 de diciembre?»\n• «descanso el domingo»\n• «el arriendo es 800 mil el día 1»"
}
