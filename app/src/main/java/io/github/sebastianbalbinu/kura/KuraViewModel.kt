package io.github.sebastianbalbinu.kura

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sebastianbalbinu.kura.data.Ajustes
import io.github.sebastianbalbinu.kura.data.Almacen
import io.github.sebastianbalbinu.kura.data.Cobro
import io.github.sebastianbalbinu.kura.data.Cofre
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.MensajeChat
import io.github.sebastianbalbinu.kura.data.Movimiento
import io.github.sebastianbalbinu.kura.data.Oficio
import io.github.sebastianbalbinu.kura.data.PagoFijo
import io.github.sebastianbalbinu.kura.engine.Calculos
import io.github.sebastianbalbinu.kura.engine.Intencion
import io.github.sebastianbalbinu.kura.engine.Interprete
import io.github.sebastianbalbinu.kura.engine.Kenzo
import io.github.sebastianbalbinu.kura.engine.ahora
import io.github.sebastianbalbinu.kura.engine.fechaCorta
import io.github.sebastianbalbinu.kura.engine.hoy
import io.github.sebastianbalbinu.kura.engine.mesClave
import io.github.sebastianbalbinu.kura.engine.oficioPor
import io.github.sebastianbalbinu.kura.engine.pesos
import io.github.sebastianbalbinu.kura.ia.EstadoIA
import io.github.sebastianbalbinu.kura.ia.KenzoIA
import io.github.sebastianbalbinu.kura.ia.Modelo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.min

/** Un botón que Kenzō ofrece debajo de un mensaje. */
data class Opcion(val etiqueta: String, val principal: Boolean = false, val accion: () -> Unit)

class KuraViewModel(app: Application) : AndroidViewModel(app) {

    private val almacen = Almacen(app)
    private val _estado = MutableStateFlow(almacen.cargar())
    val estado: StateFlow<Estado> = _estado

    val ia = KenzoIA(app)
    val actualizador = Actualizador(app)
    val opciones = mutableStateMapOf<Long, List<Opcion>>()
    val campana = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val compartir = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val pensando = MutableStateFlow(false)

    private var siguienteId = ahora()
    private fun nuevoId() = ++siguienteId

    init {
        viewModelScope.launch { actualizador.buscar() }
        // Si ya hay un modelo descargado, se carga en silencio.
        val m = ia.elegido(_estado.value.ajustes.modeloIa)
        if (m != null && ia.descargado(m)) viewModelScope.launch { ia.cargar(m) }
    }

    // ------------------------------------------------------------------ guardar
    private fun cambiar(f: (Estado) -> Estado) {
        _estado.update(f)
        val copia = _estado.value
        viewModelScope.launch(Dispatchers.IO) { almacen.guardar(copia) }
    }

    private fun kenzo(texto: String, desglose: List<Pair<String, String>> = emptyList(), ops: List<Opcion> = emptyList()): Long {
        val id = nuevoId()
        cambiar { it.copy(chat = it.chat + MensajeChat(id, true, texto, ahora(), desglose)) }
        if (ops.isNotEmpty()) opciones[id] = ops
        return id
    }

    private fun quitarOpciones(id: Long) { opciones.remove(id) }

    fun marcarBienvenida() = cambiar { it.copy(bienvenidaVista = true) }

    // ------------------------------------------------------------------ hablarle a Kenzō
    fun enviar(texto: String) {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return
        if (!_estado.value.bienvenidaVista) marcarBienvenida()
        cambiar { it.copy(chat = it.chat + MensajeChat(nuevoId(), false, limpio, ahora())) }
        val intenciones = Interprete.entender(limpio, _estado.value)
        val desconocidas = intenciones.filterIsInstance<Intencion.Desconocido>()
        intenciones.filter { it !is Intencion.Desconocido }.forEach { atender(it) }
        if (desconocidas.isNotEmpty() && intenciones.size == desconocidas.size) conIA(limpio)
    }

    private fun atender(i: Intencion) {
        val e = _estado.value
        when (i) {
            is Intencion.Ingreso -> {
                if (i.oficioId == null) preguntarOficio(i) else registrarIngreso(i.monto, i.oficioId, i.nota, i.horas)
            }
            is Intencion.Gasto -> registrarGasto(i.monto, i.oficioId, i.cofre, i.nota)
            is Intencion.PagoDeFijo -> pagarFijo(i.pagoId, i.monto)
            is Intencion.NuevoCobro -> {
                val c = Cobro(nuevoId(), i.cliente, i.monto, ahora(), i.oficioId)
                cambiar { it.copy(cobros = it.cobros + c) }
                kenzo("Anotado, señor: ${i.cliente} le debe ${pesos(i.monto)}. Si en una semana no ha pagado, se lo recuerdo.")
            }
            is Intencion.CobroPagado -> cobroPagado(i.cobroId, i.monto)
            is Intencion.Pregunta -> responderPregunta(i.monto, i.fecha)
            is Intencion.Descanso -> {
                cambiar { it.copy(ajustes = it.ajustes.copy(descansos = it.ajustes.descansos + i.fechas.map(LocalDate::toString))) }
                val dias = i.fechas.joinToString(", ") { d ->
                    if (d == hoy()) "hoy" else d.dayOfWeek.getDisplayName(TextStyle.FULL, java.util.Locale.forLanguageTag("es-CO")) + " " + d.dayOfMonth
                }
                val meta = Calculos.metaDelDia(_estado.value)
                kenzo("Anotado, señor: descansa $dias. Ya recalculé las cuentas" +
                    if (!meta.descansa && meta.meta > 0) ": la meta de hoy queda en ${pesos(meta.meta)}." else ".")
            }
            is Intencion.Tarifa -> {
                actualizarOficio(e.oficioPor(i.oficioId)!!.copy(tarifaHora = i.tarifa))
                kenzo("Entendido: en ${e.oficioPor(i.oficioId)!!.nombre} se hace unos ${pesos(i.tarifa)} por hora. Con lo que vaya registrando, afinaré ese número.")
            }
            is Intencion.SueldoFijo -> {
                actualizarOficio(e.oficioPor(i.oficioId)!!.copy(montoFijo = i.monto, diaPago = i.dia))
                kenzo("Entendido: ${e.oficioPor(i.oficioId)!!.nombre} le paga ${pesos(i.monto)} cada día ${i.dia}. Ya lo cuento como plata segura.")
            }
            is Intencion.NuevoPagoFijo -> {
                cambiar { it.copy(pagosFijos = it.pagosFijos + PagoFijo(nuevoId(), i.nombre, i.monto, i.dia)) }
                val falta = Calculos.faltaFijos(_estado.value)
                kenzo("Anotado: ${i.nombre}, ${pesos(i.monto)} cada día ${i.dia}. Iré apartando de cada ingreso" +
                    if (falta > 0) "; este mes faltan ${pesos(falta)} para cubrir los pagos fijos." else ".")
            }
            Intencion.Resumen -> resumen()
            is Intencion.Desconocido -> Unit
        }
    }

    private fun preguntarOficio(i: Intencion.Ingreso) {
        var id = 0L
        val ops = _estado.value.oficios.map { o ->
            Opcion("${o.emoji} ${o.nombre}") { quitarOpciones(id); registrarIngreso(i.monto, o.id, i.nota, i.horas) }
        }
        id = kenzo("¿De cuál oficio fueron los ${pesos(i.monto)}, señor?", ops = ops)
    }

    // ------------------------------------------------------------------ movimientos
    fun registrarIngreso(monto: Long, oficioId: String?, nota: String, horas: Double = 0.0) {
        val e = _estado.value
        val oficio = e.oficioPor(oficioId)
        val reparto = Calculos.repartir(e, monto, oficio)
        val mov = Movimiento(nuevoId(), ahora(), monto, true, oficioId, null, nota, horas, reparto)
        cambiar { s ->
            s.copy(movimientos = s.movimientos + mov,
                cofres = s.cofres.mapValues { (c, v) -> v + (reparto[c] ?: 0) })
        }
        campana.tryEmit(Unit)
        var id = 0L
        id = kenzo(Kenzo.recibido(monto, oficio?.nombre), Kenzo.desgloseReparto(reparto),
            listOf(Opcion("Deshacer") { quitarOpciones(id); deshacer(mov.id) }))
        val meta = Calculos.metaDelDia(_estado.value)
        if (!meta.descansa && meta.meta > 0 && meta.lleva >= meta.meta && meta.lleva - monto < meta.meta)
            kenzo("Meta de hoy cumplida, señor. Lo que haga de aquí en adelante es extra.")
    }

    /** Saca la plata del cofre indicado; si no alcanza, de lo libre y luego del colchón. */
    private fun sacar(cofres: Map<Cofre, Long>, monto: Long, primero: Cofre): Map<Cofre, Long> {
        val orden = listOf(primero, Cofre.LIBRE, Cofre.COLCHON).distinct()
        var falta = monto
        val tomado = linkedMapOf<Cofre, Long>()
        for (c in orden) {
            val t = min(falta, maxOf(0L, cofres[c] ?: 0))
            if (t > 0) { tomado[c] = t; falta -= t }
        }
        if (falta > 0) tomado[Cofre.LIBRE] = (tomado[Cofre.LIBRE] ?: 0) + falta
        return tomado
    }

    fun registrarGasto(monto: Long, oficioId: String?, cofre: Cofre, nota: String, pagoFijoId: Long? = null) {
        val e = _estado.value
        val tomado = sacar(e.cofres, monto, cofre)
        val mov = Movimiento(nuevoId(), ahora(), monto, false, oficioId, cofre, nota, 0.0, tomado, pagoFijoId)
        cambiar { s ->
            s.copy(movimientos = s.movimientos + mov,
                cofres = s.cofres.mapValues { (c, v) -> v - (tomado[c] ?: 0) },
                pagados = if (pagoFijoId == null) s.pagados else
                    s.pagados + (mesClave() to ((s.pagados[mesClave()] ?: emptySet()) + pagoFijoId)))
        }
        campana.tryEmit(Unit)
        val libre = Calculos.cofre(_estado.value, Cofre.LIBRE)
        val origen = tomado.keys.joinToString(" y ") { it.nombre.lowercase() }
        var id = 0L
        val texto = "Anotado: ${pesos(monto)} de $origen." + when {
            libre < 0 -> " El cofre libre quedó en ${pesos(libre)}; le sugiero ir con calma unos días."
            pagoFijoId != null -> ""
            else -> " Le quedan ${pesos(libre)} libres."
        }
        id = kenzo(texto, ops = listOf(Opcion("Deshacer") { quitarOpciones(id); deshacer(mov.id) }))
    }

    fun pagarFijo(pagoId: Long, monto: Long) {
        val p = _estado.value.pagosFijos.firstOrNull { it.id == pagoId } ?: return
        if (pagoId in Calculos.pagadosMes(_estado.value)) {
            kenzo("${p.nombre} ya figura pagado este mes, señor."); return
        }
        registrarGasto(monto, null, Cofre.FIJOS, "Pago de ${p.nombre}", pagoId)
    }

    fun cobroPagado(cobroId: Long, monto: Long? = null) {
        val c = _estado.value.cobros.firstOrNull { it.id == cobroId } ?: return
        cambiar { s -> s.copy(cobros = s.cobros.map { if (it.id == cobroId) it.copy(pagado = true) else it }) }
        registrarIngreso(monto ?: c.monto, c.oficioId, "Pago de ${c.cliente}")
    }

    fun deshacer(movId: Long) {
        val m = _estado.value.movimientos.firstOrNull { it.id == movId } ?: return
        cambiar { s ->
            s.copy(
                movimientos = s.movimientos.filter { it.id != movId },
                cofres = s.cofres.mapValues { (c, v) -> if (m.ingreso) v - (m.reparto[c] ?: 0) else v + (m.reparto[c] ?: 0) },
                pagados = if (m.pagoFijoId == null) s.pagados else
                    s.pagados.mapValues { (_, ids) -> ids - m.pagoFijoId }
            )
        }
        kenzo("Listo, señor. Lo deshice y devolví todo a su cofre.")
    }

    // ------------------------------------------------------------------ preguntas
    private fun responderPregunta(monto: Long, fecha: LocalDate) {
        val r = Calculos.puedoPagar(_estado.value, monto, fecha)
        val cuando = if (fecha == hoy()) "hoy" else "el ${fechaCorta(fecha)}"
        val texto = when (r.nivel) {
            0 -> "Sí, señor, con tranquilidad. Puede pagar ${pesos(monto)} $cuando."
            1 -> "Sí se puede, pero tocaría el colchón." + (r.sugerencia?.let { " Mejor el ${fechaCorta(it)}, cuando ya no haga falta." } ?: "")
            else -> "Por ahora no, señor." + (r.sugerencia?.let { " Sería posible desde el ${fechaCorta(it)}." } ?: " Primero hay que completar los pagos fijos.")
        } + "\nSolo conté plata segura: no Uber ni clientes nuevos."
        val resumen = texto.substringBefore("\n") + "\n" + r.desglose.joinToString("\n") { "${it.first}: ${it.second}" }
        kenzo(texto, r.desglose, listOf(Opcion("Enviarlo por WhatsApp", principal = true) { compartir.tryEmit("Kenzō dice: $resumen") }))
    }

    private fun resumen() {
        val e = _estado.value
        val mes = mesClave()
        val movs = e.movimientos.filter { mesClave(io.github.sebastianbalbinu.kura.engine.fechaDe(it.fecha)) == mes }
        val entro = movs.filter { it.ingreso }.sumOf { it.monto }
        val salio = movs.filter { !it.ingreso }.sumOf { it.monto }
        val meta = Calculos.metaDelDia(e)
        val mejor = Calculos.resumenMes(e).filter { it.ingresos > 0 }.maxByOrNull { it.porHora }
        kenzo("Así va el mes, señor:" + (mejor?.let { "\n${it.oficio.nombre} es lo que mejor le paga por hora." } ?: ""),
            listOf("Entró" to pesos(entro), "Salió" to pesos(salio)) +
                Cofre.entries.map { "${it.kanji} ${it.nombre}" to pesos(Calculos.cofre(e, it)) } +
                listOf("Meta de hoy" to if (meta.descansa) "descanso" else pesos(meta.meta)))
    }

    // ------------------------------------------------------------------ IA
    private val sistemaKenzo = """
        Eres Kenzō, el mayordomo japonés y administrador de confianza de un trabajador independiente colombiano.
        Hablas en español de Colombia, con respeto, calma y pocas palabras (máximo 3 frases). Le dices "señor".
        Nunca inventes cifras: usa solo los datos que te doy. Si algo no está en los datos, dilo con humildad.
    """.trimIndent()

    private fun contexto(e: Estado): String {
        val meta = Calculos.metaDelDia(e)
        return buildString {
            appendLine("Datos de hoy (${hoy()}):")
            Cofre.entries.forEach { appendLine("- Cofre ${it.nombre}: ${pesos(Calculos.cofre(e, it))}") }
            appendLine("- Meta de hoy: ${pesos(meta.meta)}, lleva ${pesos(meta.lleva)}")
            appendLine("- Puede gastar hoy: ${pesos(Calculos.puedeGastarHoy(e))}")
            Calculos.fijosPendientesMes(e).forEach { appendLine("- Pago fijo pendiente: ${it.nombre} ${pesos(it.monto)} el día ${it.dia}") }
            e.cobros.filter { !it.pagado }.forEach { appendLine("- ${it.cliente} le debe ${pesos(it.monto)}") }
            Calculos.resumenMes(e).forEach { appendLine("- ${it.oficio.nombre}: ${pesos(it.ingresos)} este mes, ~${pesos(it.porHora)}/hora") }
        }
    }

    private fun conIA(texto: String) {
        if (!ia.lista) { kenzo(Kenzo.ayuda()); return }
        viewModelScope.launch {
            pensando.value = true
            val e = _estado.value
            val claves = e.oficios.joinToString(", ") { it.id }
            val extraccion = ia.preguntar(
                "Conviertes frases de un trabajador colombiano en UNA línea con formato TIPO|MONTO|OFICIO. " +
                    "TIPO es INGRESO, GASTO o PREGUNTA. MONTO es un entero en pesos (\"450\" o \"450 mil\" = 450000). " +
                    "OFICIO es uno de: $claves, o NINGUNO. Si la frase no es un movimiento de dinero responde NADA. No escribas nada más.",
                "Ejemplos:\n«me consignaron 300 por el logo» → INGRESO|300000|diseno\n«se me fueron 20 en el almuerzo» → GASTO|20000|NINGUNO\n«hola kenzo» → NADA\n\nFrase: «$texto»",
                temperatura = 0.1
            )
            val m = extraccion?.let { Regex("""(INGRESO|GASTO)\|(\d+)\|(\w+)""").find(it) }
            if (m != null) {
                val monto = m.groupValues[2].toLong()
                val of = e.oficioPor(m.groupValues[3])
                val ingreso = m.groupValues[1] == "INGRESO"
                var id = 0L
                id = kenzo("Creo entender: ${if (ingreso) "entraron" else "salieron"} ${pesos(monto)}" +
                    (of?.let { " (${it.nombre})" } ?: "") + ". ¿Lo anoto así, señor?",
                    ops = listOf(
                        Opcion("Sí, anótelo", principal = true) {
                            quitarOpciones(id)
                            if (ingreso) { if (of == null) preguntarOficio(Intencion.Ingreso(monto, null, texto)) else registrarIngreso(monto, of.id, texto) }
                            else registrarGasto(monto, of?.id, if (of != null && of.pctNegocio > 0) Cofre.NEGOCIO else Cofre.LIBRE, texto)
                        },
                        Opcion("No") { quitarOpciones(id); kenzo("Entendido, no anoto nada. Dígamelo de otra forma, por favor.") }
                    ))
            } else {
                val r = ia.preguntar(sistemaKenzo, contexto(e) + "\nEl señor dice: «$texto»", temperatura = 0.5)
                kenzo(if (r.isNullOrBlank()) Kenzo.ayuda() else r)
            }
            pensando.value = false
        }
    }

    fun descargarIA(m: Modelo) {
        cambiar { it.copy(ajustes = it.ajustes.copy(modeloIa = m.clave)) }
        viewModelScope.launch { ia.descargarYCargar(m) }
    }

    fun usarIA(clave: String) {
        cambiar { it.copy(ajustes = it.ajustes.copy(modeloIa = clave)) }
        val m = ia.elegido(clave)
        if (m == null) ia.apagar()
        else if (ia.descargado(m)) viewModelScope.launch { ia.cargar(m) }
    }

    fun medirIA() = viewModelScope.launch {
        pensando.value = true
        val v = ia.medirVelocidad()
        pensando.value = false
        val m = (ia.estado.value as? EstadoIA.Lista)?.modelo
        kenzo(when {
            v == null -> "No pude medir la velocidad, señor."
            v < 6 && m == Modelo.Q2B -> "En este teléfono escribo unas ${v.toInt()} letras por segundo. Es lento; le sugiero el modelo 0.8B."
            else -> "En este teléfono escribo unas ${v.toInt()} letras por segundo. Funciona bien."
        })
    }

    // ------------------------------------------------------------------ ajustes y edición
    fun actualizarOficio(o: Oficio) = cambiar { s ->
        s.copy(oficios = if (s.oficios.any { it.id == o.id }) s.oficios.map { if (it.id == o.id) o else it } else s.oficios + o)
    }

    fun borrarOficio(id: String) = cambiar { s -> s.copy(oficios = s.oficios.filter { it.id != id }) }

    fun guardarPagoFijo(p: PagoFijo) = cambiar { s ->
        s.copy(pagosFijos = if (s.pagosFijos.any { it.id == p.id }) s.pagosFijos.map { if (it.id == p.id) p else it } else s.pagosFijos + p)
    }

    fun borrarPagoFijo(id: Long) = cambiar { s -> s.copy(pagosFijos = s.pagosFijos.filter { it.id != id }) }

    fun nuevoCobro(cliente: String, monto: Long, oficioId: String?) =
        cambiar { s -> s.copy(cobros = s.cobros + Cobro(nuevoId(), cliente, monto, ahora(), oficioId)) }

    fun borrarCobro(id: Long) = cambiar { s -> s.copy(cobros = s.cobros.filter { it.id != id }) }

    fun ajustar(f: (Ajustes) -> Ajustes) = cambiar { s -> s.copy(ajustes = f(s.ajustes)) }

    fun alternarDescanso(d: LocalDate) = ajustar { a ->
        val k = d.toString(); a.copy(descansos = if (k in a.descansos) a.descansos - k else a.descansos + k)
    }

    fun idNuevo() = nuevoId()

    fun buscarActualizacion() = viewModelScope.launch {
        if (!actualizador.buscar()) kenzo("Kura está al día, señor: tiene la versión ${actualizador.nombreActual}.")
    }

    fun actualizar() {
        when (val s = actualizador.estado.value) {
            is EstadoActualizacion.Disponible -> viewModelScope.launch { actualizador.descargar(s.v) }
            is EstadoActualizacion.Lista -> actualizador.instalar(s.archivo)
            else -> Unit
        }
    }
}
