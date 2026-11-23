package io.github.sebastianbalbinu.kura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.KuraViewModel
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.MensajeChat
import io.github.sebastianbalbinu.kura.ia.EstadoIA

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaKenzo(e: Estado, vm: KuraViewModel) {
    val lista = rememberLazyListState()
    val pensando by vm.pensando.collectAsState()
    val ia by vm.ia.estado.collectAsState()
    LaunchedEffect(e.chat.size, pensando) {
        if (e.chat.isNotEmpty()) lista.animateScrollToItem(e.chat.size - 1 + if (pensando) 1 else 0)
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hanko("K", 34.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Kenzō", style = Letra.titulo.copy(fontSize = 20.sp))
                Text(
                    when (val s = ia) {
                        is EstadoIA.Lista -> "su administrador · ${s.modelo.nombre}"
                        is EstadoIA.Cargando -> "probando su mente…"
                        is EstadoIA.Descargando -> "aprendiendo · ${(s.progreso * 100).toInt()}%"
                        else -> "su administrador"
                    }, style = Letra.suave.copy(fontSize = 11.sp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(state = lista, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (e.chat.isEmpty()) item {
                TarjetaKenzo("Aquí estoy, señor. Cuénteme cada movimiento como se lo contaría a una persona: «me pagaron 200 de la web», «eché 15 de gasolina», «¿puedo pagar 150 el viernes?». Yo organizo todo.")
            }
            items(e.chat, key = { it.id }) { m -> Burbuja(m, vm) }
            if (pensando) item { Text("Kenzō está pensando… (con IA puede tardar un minuto)", style = Letra.suave.copy(fontFamily = Mincho), modifier = Modifier.padding(6.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Burbuja(m: MensajeChat, vm: KuraViewModel) {
    if (!m.deKenzo) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(m.texto, style = Letra.cuerpo,
                modifier = Modifier.widthIn(max = 300.dp)
                    .background(Color(0x29C9A96E), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .border(1.dp, Color(0x4DC9A96E), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 15.dp, vertical = 11.dp))
        }
        return
    }
    Column(Modifier.widthIn(max = 330.dp).vidrio(18.dp).padding(15.dp)) {
        Text(m.texto, style = Letra.cuerpo.copy(color = Color(0xFFE2D8C6), fontSize = 14.5.sp))
        if (m.desglose.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            m.desglose.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .background(Color(0x0DF3E7D3), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(k, style = Letra.suave.copy(color = K.textoSuave), modifier = Modifier.weight(1f))
                    Text(v, style = Letra.suave.copy(color = K.texto))
                }
            }
        }
        val ops = vm.opciones[m.id]
        if (!ops.isNullOrEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ops.forEach { o ->
                    if (o.principal) BotonDorado(o.etiqueta) { o.accion() } else BotonSuave(o.etiqueta) { o.accion() }
                }
            }
        }
    }
}
