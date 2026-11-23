package io.github.sebastianbalbinu.kura.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.KuraViewModel
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.Oficio
import io.github.sebastianbalbinu.kura.data.TipoOficio
import io.github.sebastianbalbinu.kura.engine.Calculos
import io.github.sebastianbalbinu.kura.engine.hoy
import io.github.sebastianbalbinu.kura.engine.pesos
import java.time.format.TextStyle

@Composable
fun PantallaOficios(e: Estado, vm: KuraViewModel) {
    var editar by remember { mutableStateOf<Oficio?>(null) }
    val resumen = remember(e) { Calculos.resumenMes(e) }
    val mes = hoy().month.getDisplayName(TextStyle.FULL, io.github.sebastianbalbinu.kura.engine.ES)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("Sus oficios", style = Letra.titulo)
        Text("Cuánto le dejó cada trabajo en $mes, y por hora.", style = Letra.suave)
        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth().vidrio().padding(horizontal = 16.dp, vertical = 4.dp)) {
            resumen.forEach { r ->
                val o = r.oficio
                Row(Modifier.fillMaxWidth().clickable { editar = o }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(o.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.nombre, style = Letra.cuerpo)
                        Text(
                            when (o.tipo) {
                                TipoOficio.FIJO -> if (o.montoFijo > 0) "Pago fijo · el ${o.diaPago}" else "Toque para poner cuánto y qué día"
                                TipoOficio.HORAS -> if (r.horas > 0) "${"%.1f".format(r.horas)} horas registradas" else "Dígale las horas: «uber 85, 5 horas»"
                                else -> if (r.veces > 0) "${r.veces} ingresos" else o.tipo.etiqueta
                            }, style = Letra.suave.copy(fontSize = 12.sp)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(pesos(r.ingresos), style = Letra.numero.copy(fontSize = 16.sp))
                        if (r.porHora > 0) Text("${pesos(r.porHora)} / h", style = Letra.suave.copy(color = K.oro, fontSize = 11.5.sp))
                    }
                }
            }
        }

        val mejor = resumen.filter { it.porHora > 0 && (it.ingresos > 0 || it.oficio.tarifaHora > 0) }.maxByOrNull { it.porHora }
        val peor = resumen.filter { it.porHora > 0 }.minByOrNull { it.porHora }
        if (mejor != null && peor != null && mejor != peor && peor.porHora > 0) {
            Spacer(Modifier.height(12.dp))
            val equivale = mejor.porHora.toFloat() / peor.porHora
            TarjetaKenzo("${mejor.oficio.nombre} es lo que más le paga por hora. Una hora ahí equivale a ${"%.1f".format(equivale)} horas de ${peor.oficio.nombre.lowercase()}.")
        }

        Spacer(Modifier.height(14.dp))
        BotonSuave("+ Agregar oficio", Modifier.fillMaxWidth()) {
            editar = Oficio("o" + vm.idNuevo(), "", "✦", TipoOficio.PROYECTOS)
        }
        Text("Toque un oficio para cambiar su nombre, tarifa por hora o el porcentaje que va al cofre del negocio.",
            style = Letra.suave.copy(fontSize = 12.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 28.dp))
    }

    editar?.let { o ->
        DialogoOficio(o, nuevo = e.oficios.none { it.id == o.id },
            alGuardar = { vm.actualizarOficio(it); editar = null },
            alBorrar = { vm.borrarOficio(o.id); editar = null },
            alCerrar = { editar = null })
    }
}
