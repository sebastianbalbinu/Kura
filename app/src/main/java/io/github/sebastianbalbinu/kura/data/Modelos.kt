package io.github.sebastianbalbinu.kura.data

/** Cómo gana plata cada oficio. */
enum class TipoOficio(val etiqueta: String) {
    HORAS("Por horas"),        // Uber, domicilios…
    PROYECTOS("Por proyecto"), // diseño, web…
    FIJO("Pago fijo"),         // la agencia, un sueldo
    VENTAS("Por ventas")       // el café
}

/** Los cofres de la bodega. Cada peso que entra cae en uno. */
enum class Cofre(val icono: String, val nombre: String, val sub: String) {
    FIJOS("📅", "Pagos fijos", "Lo de cada mes, apartado"),
    NEGOCIO("💼", "Negocio", "Reponer y pagar proveedores"),
    COLCHON("🛟", "Colchón", "Para los meses flojos"),
    LIBRE("🍃", "Libre", "Suyo, sin culpa")
}

data class Oficio(
    val id: String,
    val nombre: String,
    val emoji: String,
    val tipo: TipoOficio,
    /** Lo que el usuario dice que se hace por hora (punto de partida). */
    val tarifaHora: Long = 0,
    /** Porcentaje de cada ingreso que va al cofre del negocio (costos). */
    val pctNegocio: Int = 0,
    /** Palabras que hacen que Kenzō reconozca este oficio. */
    val palabras: List<String> = emptyList(),
    /** Solo para FIJO: día del mes en que pagan y cuánto. */
    val diaPago: Int = 0,
    val montoFijo: Long = 0
)

data class Movimiento(
    val id: Long,
    val fecha: Long,
    val monto: Long,
    val ingreso: Boolean,
    val oficioId: String? = null,
    /** Para gastos: de qué cofre salió. */
    val cofre: Cofre? = null,
    val nota: String = "",
    val horas: Double = 0.0,
    /** Para ingresos: cómo se repartió en los cofres. */
    val reparto: Map<Cofre, Long> = emptyMap(),
    /** Si fue el pago de un pago fijo. */
    val pagoFijoId: Long? = null
)

data class PagoFijo(val id: Long, val nombre: String, val monto: Long, val dia: Int)

data class Cobro(
    val id: Long,
    val cliente: String,
    val monto: Long,
    val desde: Long,
    val oficioId: String? = null,
    val pagado: Boolean = false
)

data class Ajustes(
    val colchonPct: Int = 10,
    /** Cuánto quiere tener libre para gastar en un mes normal. */
    val libreMensual: Long = 600_000,
    /** Días de descanso, como "2026-11-30". */
    val descansos: Set<String> = emptySet(),
    /** "auto", "ninguno", "q08b" o "q2b". */
    val modeloIa: String = "auto",
    val sonido: Boolean = true
)

data class MensajeChat(
    val id: Long,
    val deKenzo: Boolean,
    val texto: String,
    val fecha: Long,
    /** Líneas tipo "concepto → valor" para mostrar un desglose. */
    val desglose: List<Pair<String, String>> = emptyList()
)

data class Estado(
    val oficios: List<Oficio> = oficiosIniciales(),
    val movimientos: List<Movimiento> = emptyList(),
    val pagosFijos: List<PagoFijo> = emptyList(),
    /** Mes ("2026-11") → ids de pagos fijos ya pagados ese mes. */
    val pagados: Map<String, Set<Long>> = emptyMap(),
    val cobros: List<Cobro> = emptyList(),
    val cofres: Map<Cofre, Long> = Cofre.entries.associateWith { 0L },
    val ajustes: Ajustes = Ajustes(),
    val chat: List<MensajeChat> = emptyList(),
    val bienvenidaVista: Boolean = false
)

fun oficiosIniciales() = listOf(
    Oficio("uber", "Uber", "🏍️", TipoOficio.HORAS, tarifaHora = 18_000, pctNegocio = 15,
        palabras = listOf("uber", "moto", "carrera", "carreras", "turno", "didi", "indrive")),
    Oficio("cafe", "Espiral Café", "☕", TipoOficio.VENTAS, pctNegocio = 40,
        palabras = listOf("cafe", "bolsa", "bolsas", "libra", "espiral", "tostado")),
    Oficio("diseno", "Diseño", "🎨", TipoOficio.PROYECTOS, tarifaHora = 28_000, pctNegocio = 0,
        palabras = listOf("diseno", "logo", "aviso", "adhesivo", "calcomania", "vinilo", "flyer", "volante")),
    Oficio("web", "Páginas web", "🌐", TipoOficio.PROYECTOS, tarifaHora = 45_000,
        palabras = listOf("web", "pagina", "hosting", "dominio", "mantenimiento", "landing")),
    Oficio("agencia", "Agencia", "🎬", TipoOficio.FIJO, tarifaHora = 20_000,
        palabras = listOf("agencia", "sueldo", "salario", "nomina"), diaPago = 30, montoFijo = 0)
)
