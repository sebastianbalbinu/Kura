package io.github.sebastianbalbinu.kura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.KuraViewModel
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.engine.ES
import io.github.sebastianbalbinu.kura.engine.hoy
import io.github.sebastianbalbinu.kura.engine.pesos
import io.github.sebastianbalbinu.kura.ia.EstadoIA
import io.github.sebastianbalbinu.kura.ia.Modelo
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaAjustes(e: Estado, vm: KuraViewModel, alVolver: () -> Unit) {
    val ia by vm.ia.estado.collectAsState()
    val ram = remember { vm.ia.ramGb() }
    val recomendado = remember { vm.ia.recomendado() }
    var editLibre by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = K.oro, fontSize = 22.sp, modifier = Modifier.clickable { alVolver() }.padding(end = 14.dp))
            Text("Ajustes", style = Letra.titulo)
        }

        // ---------------- la mente de Kenzō ----------------
        Titulito("LA MENTE DE KENZŌ", null) {}
        Column(Modifier.fillMaxWidth().vidrio().padding(16.dp)) {
            Text("Su teléfono tiene ${"%.1f".format(ram)} GB de RAM.", style = Letra.cuerpo)
            Text(
                when (recomendado) {
                    null -> "Le recomiendo Kenzō sin IA: es rápido y igual entiende lo esencial."
                    else -> "Le recomiendo ${recomendado.nombre} (${recomendado.tamanoMb} MB). Se descarga solo con wifi."
                }, style = Letra.suave
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (val s = ia) {
                    is EstadoIA.Lista -> "✓ Activa: ${s.modelo.nombre}" + (s.velocidad?.let { " · ${it.toInt()} letras/seg" } ?: "")
                    is EstadoIA.Cargando -> "Cargando ${s.modelo.nombre}…"
                    is EstadoIA.Descargando -> "Descargando ${s.modelo.nombre}: ${(s.progreso * 100).toInt()}%"
                    is EstadoIA.Error -> s.mensaje
                    EstadoIA.Apagada -> "Kenzō funciona sin IA."
                    EstadoIA.SinModelo -> "Aún no hay modelo descargado."
                }, style = Letra.cuerpo.copy(color = K.oro, fontSize = 14.sp)
            )
            if (ia is EstadoIA.Descargando) {
                Spacer(Modifier.height(8.dp)); BarraArroz((ia as EstadoIA.Descargando).progreso)
            }
            Spacer(Modifier.height(12.dp))
            Modelo.entries.forEach { m ->
                val activo = (ia as? EstadoIA.Lista)?.modelo == m
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.nombre + if (m == recomendado) "  · recomendado" else "", style = Letra.cuerpo.copy(fontSize = 14.sp))
                        Text("${m.tamanoMb} MB" + if (vm.ia.descargado(m)) " · descargado" else "", style = Letra.suave.copy(fontSize = 12.sp))
                    }
                    when {
                        activo -> Text("en uso", color = K.verde, fontSize = 13.sp)
                        vm.ia.descargado(m) -> BotonSuave("Usar") { vm.usarIA(m.clave) }
                        ia is EstadoIA.Descargando -> {}
                        else -> BotonSuave("Descargar") { vm.descargarIA(m) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ia is EstadoIA.Lista) BotonSuave("Medir velocidad") { vm.medirIA() }
                if (ia is EstadoIA.Descargando) BotonSuave("Cancelar descarga") { vm.ia.cancelarDescarga() }
                BotonSuave(if (e.ajustes.modeloIa == "ninguno") "✓ Sin IA" else "Usar sin IA") { vm.usarIA("ninguno") }
            }
            Spacer(Modifier.height(10.dp))
            Text("La IA corre dentro de su teléfono: nada sale de aquí. Las cuentas siempre las hace el código; la IA solo entiende frases y conversa.",
                style = Letra.suave.copy(fontSize = 12.sp))
        }

        // ---------------- días de descanso ----------------
        Titulito("DÍAS DE DESCANSO", null) {}
        Text("Toque los días que no va a trabajar. Kenzō reparte la meta entre los demás.", style = Letra.suave)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            (0L until 21L).map { hoy().plusDays(it) }.forEach { d ->
                val sel = d.toString() in e.ajustes.descansos
                Box(
                    Modifier.size(width = 46.dp, height = 54.dp)
                        .background(if (sel) Color(0x33B8432B) else Color(0x0DF3E7D3), RoundedCornerShape(12.dp))
                        .border(1.dp, if (sel) K.bermellon else Color(0x1AF3E7D3), RoundedCornerShape(12.dp))
                        .clickable { vm.alternarDescanso(d) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).take(3), fontSize = 10.sp, color = K.textoTenue)
                        Text("${d.dayOfMonth}", fontSize = 16.sp, color = if (sel) K.bermellonClaro else K.texto, fontFamily = Mincho)
                    }
                }
            }
        }

        // ---------------- cómo se reparte ----------------
        Titulito("CÓMO SE REPARTE", null) {}
        Column(Modifier.fillMaxWidth().vidrio().padding(16.dp)) {
            var colchon by remember(e.ajustes.colchonPct) { mutableFloatStateOf(e.ajustes.colchonPct.toFloat()) }
            Text("Al colchón: ${colchon.toInt()}% de cada ingreso", style = Letra.cuerpo.copy(fontSize = 14.sp))
            Slider(colchon, { colchon = it }, valueRange = 0f..30f, steps = 29,
                onValueChangeFinished = { vm.ajustar { a -> a.copy(colchonPct = colchon.toInt()) } },
                colors = SliderDefaults.colors(thumbColor = K.oro, activeTrackColor = K.oro, inactiveTrackColor = Color(0x22F3E7D3)))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Libre para gastar en un mes normal", style = Letra.cuerpo.copy(fontSize = 14.sp))
                    Text(pesos(e.ajustes.libreMensual), style = Letra.numero)
                }
                BotonSuave("Cambiar") { editLibre = true }
            }
            Spacer(Modifier.height(6.dp))
            Text("Con esto Kenzō calcula su meta diaria: primero los pagos fijos, luego su plata libre del mes.",
                style = Letra.suave.copy(fontSize = 12.sp))
        }

        Titulito("OTROS", null) {}
        Row(Modifier.fillMaxWidth().vidrio().clickable { vm.ajustar { it.copy(sonido = !it.sonido) } }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Campanita al guardar", style = Letra.cuerpo, modifier = Modifier.weight(1f))
            Text(if (e.ajustes.sonido) "sí" else "no", color = K.oro)
        }
        Spacer(Modifier.height(30.dp))
    }

    if (editLibre) DialogoMonto("Libre al mes", e.ajustes.libreMensual,
        alGuardar = { v -> vm.ajustar { it.copy(libreMensual = v) }; editLibre = false }, alCerrar = { editLibre = false })
}
