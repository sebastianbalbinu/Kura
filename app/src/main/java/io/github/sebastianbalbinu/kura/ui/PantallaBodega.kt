package io.github.sebastianbalbinu.kura.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.KuraViewModel
import io.github.sebastianbalbinu.kura.data.Cofre
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.PagoFijo
import io.github.sebastianbalbinu.kura.engine.Calculos
import io.github.sebastianbalbinu.kura.engine.fechaDe
import io.github.sebastianbalbinu.kura.engine.hoy
import io.github.sebastianbalbinu.kura.engine.oficioPor
import io.github.sebastianbalbinu.kura.engine.pesos
import java.time.temporal.ChronoUnit

@Composable
fun PantallaBodega(e: Estado, vm: KuraViewModel) {
    var editarPago by remember { mutableStateOf<PagoFijo?>(null) }
    var nuevoCobro by remember { mutableStateOf(false) }
    val pagados = Calculos.pagadosMes(e)
    val totalFijos = e.pagosFijos.sumOf { it.monto }
    val maxOtros = maxOf(1L, Cofre.entries.filter { it != Cofre.FIJOS }.maxOf { Calculos.cofre(e, it) })

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("La bodega", style = Letra.titulo)
        Text("Cada peso que entra cae en su cofre.", style = Letra.suave)
        Spacer(Modifier.height(16.dp))

        Cofre.entries.forEach { c ->
            val v = Calculos.cofre(e, c)
            Column(Modifier.fillMaxWidth().padding(bottom = 11.dp).vidrio().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icono(c.icono)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.nombre, style = Letra.cuerpo)
                        Text(if (c == Cofre.FIJOS && totalFijos > 0) "de ${pesos(Calculos.totalPendienteMes(e))} que faltan pagar" else c.sub,
                            style = Letra.suave.copy(fontSize = 12.sp))
                    }
                    Text(pesos(v), style = Letra.numero.copy(color = if (v < 0) K.bermellonClaro else K.texto))
                }
                Spacer(Modifier.height(11.dp))
                val frac = if (c == Cofre.FIJOS) {
                    val p = Calculos.totalPendienteMes(e); if (p <= 0) 1f else v.toFloat() / p
                } else v.toFloat() / maxOtros
                BarraArroz(frac)
            }
        }

        // ---------- pagos fijos ----------
        Titulito("PAGOS FIJOS", "+ agregar") { editarPago = PagoFijo(vm.idNuevo(), "", 0, 1) }
        if (e.pagosFijos.isEmpty()) Text("Dígale a Kenzō «el arriendo es 800 mil el día 1», o toque «+ agregar».", style = Letra.suave)
        Column(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 4.dp)) {
            e.pagosFijos.sortedBy { it.dia }.forEach { p ->
                val pagado = p.id in pagados
                Row(Modifier.fillMaxWidth().clickable { editarPago = p }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.nombre, style = Letra.cuerpo)
                        Text("Día ${p.dia} · ${pesos(p.monto)}", style = Letra.suave.copy(fontSize = 12.sp))
                    }
                    if (pagado) Text("✓ pagado", color = K.verde, fontSize = 13.sp)
                    else BotonSuave("Pagar") { vm.pagarFijo(p.id, p.monto) }
                }
            }
        }

        // ---------- por cobrar ----------
        Titulito("POR COBRAR", "+ agregar") { nuevoCobro = true }
        val pendientes = e.cobros.filter { !it.pagado }
        if (pendientes.isEmpty()) Text("Nadie le debe nada. Si alguien le debe, dígale a Kenzō «Doña Marta me debe 50».", style = Letra.suave)
        else Column(Modifier.fillMaxWidth().vidrio(borde = Color(0x59B8432B)).padding(horizontal = 16.dp, vertical = 4.dp)) {
            pendientes.forEach { c ->
                val dias = ChronoUnit.DAYS.between(fechaDe(c.desde), hoy())
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.cliente, style = Letra.cuerpo)
                        Text("${pesos(c.monto)} · hace $dias días" + (e.oficioPor(c.oficioId)?.let { " · ${it.nombre}" } ?: ""),
                            style = Letra.suave.copy(fontSize = 12.sp))
                    }
                    BotonSuave("Ya pagó") { vm.cobroPagado(c.id) }
                }
            }
        }

        // ---------- últimos movimientos ----------
        Titulito("ÚLTIMOS MOVIMIENTOS", null) {}
        val ultimos = e.movimientos.takeLast(15).reversed()
        if (ultimos.isEmpty()) Text("Aún no hay movimientos.", style = Letra.suave)
        Column(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 4.dp)) {
            ultimos.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(e.oficioPor(m.oficioId)?.emoji ?: if (m.ingreso) "＋" else "－", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.nota.ifBlank { if (m.ingreso) "Ingreso" else "Gasto" }, style = Letra.cuerpo.copy(fontSize = 14.sp), maxLines = 1)
                        Text(io.github.sebastianbalbinu.kura.engine.fechaCorta(fechaDe(m.fecha)), style = Letra.suave.copy(fontSize = 11.sp))
                    }
                    Text((if (m.ingreso) "+" else "−") + pesos(m.monto),
                        style = Letra.numero.copy(fontSize = 15.sp, color = if (m.ingreso) K.verde else K.texto))
                    Spacer(Modifier.width(10.dp))
                    Text("✕", color = K.textoTenue, fontSize = 14.sp, modifier = Modifier.clickable { vm.deshacer(m.id) }.padding(4.dp))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    editarPago?.let { p ->
        DialogoPagoFijo(p, nuevo = e.pagosFijos.none { it.id == p.id },
            alGuardar = { vm.guardarPagoFijo(it); editarPago = null },
            alBorrar = { vm.borrarPagoFijo(p.id); editarPago = null },
            alCerrar = { editarPago = null })
    }
    if (nuevoCobro) DialogoCobro(e, alGuardar = { cl, m, o -> vm.nuevoCobro(cl, m, o); nuevoCobro = false }, alCerrar = { nuevoCobro = false })
}

@Composable
fun Titulito(texto: String, accion: String?, alAccion: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(texto, style = Letra.etiqueta)
        if (accion != null) Text(accion, color = K.oro, fontSize = 13.sp,
            modifier = Modifier.border(1.dp, Color(0x40C9A96E), RoundedCornerShape(10.dp)).clickable { alAccion() }
                .padding(horizontal = 10.dp, vertical = 4.dp))
    }
}
