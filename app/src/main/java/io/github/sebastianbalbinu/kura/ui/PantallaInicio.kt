package io.github.sebastianbalbinu.kura.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.engine.Calculos
import io.github.sebastianbalbinu.kura.engine.Kenzo
import io.github.sebastianbalbinu.kura.engine.fechaLarga
import io.github.sebastianbalbinu.kura.engine.hoy
import io.github.sebastianbalbinu.kura.engine.pesos
import kotlin.math.ceil

@Composable
fun PantallaInicio(e: Estado, alAjustes: () -> Unit, alKenzo: () -> Unit) {
    val meta = remember(e) { Calculos.metaDelDia(e) }
    val gastar = remember(e) { Calculos.puedeGastarHoy(e) }
    val aviso = remember(e.movimientos.size, e.pagosFijos, e.cobros, e.oficios, e.ajustes, e.bienvenidaVista) { Kenzo.aviso(e) }
    val prov = remember { Kenzo.proverbioDelDia() }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Kenzo.saludo(), style = Letra.cuerpo.copy(color = K.textoSuave))
                Text(fechaLarga(hoy()), style = Letra.suave.copy(fontFamily = Mincho, color = K.textoTenue))
            }
            Hanko("蔵", 42.dp, Modifier.clickable { alAjustes() })
        }

        Enso(
            progreso = when {
                meta.descansa -> 1f
                meta.meta <= 0 -> if (meta.lleva > 0) 1f else 0.04f
                else -> meta.lleva.toFloat() / meta.meta
            },
            modifier = Modifier.fillMaxWidth(0.86f).aspectRatio(1f).align(Alignment.CenterHorizontally).padding(top = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (meta.descansa) {
                    Text("Hoy", style = Letra.suave)
                    Text("descansa", style = Letra.numeroGrande.copy(fontSize = 38.sp))
                    Text("ya está en las cuentas", style = Letra.suave.copy(color = K.oro, fontSize = 12.sp))
                } else {
                    Text("Meta de hoy", style = Letra.suave)
                    Text(pesos(meta.meta), style = Letra.numeroGrande)
                    Text("lleva ${pesos(meta.lleva)}", style = Letra.suave.copy(fontSize = 12.sp))
                    val falta = meta.meta - meta.lleva
                    val pista = when {
                        meta.meta > 0 && falta <= 0 -> "meta cumplida"
                        meta.horasFaltan != null && meta.horasFaltan > 0.05 && meta.oficioHoras != null ->
                            "≈ ${horasBonitas(meta.horasFaltan)} de ${meta.oficioHoras.nombre.lowercase()}"
                        meta.meta == 0L -> "los cofres están en orden"
                        else -> ""
                    }
                    if (pista.isNotEmpty()) Text(pista, style = Letra.suave.copy(color = K.oro, fontSize = 12.sp),
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        Text(
            "Hoy puede gastar con tranquilidad ${pesos(gastar)}",
            style = Letra.suave.copy(color = K.textoSuave, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        TarjetaKenzo(aviso, alTocar = alKenzo)

        Column(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(prov.jp, style = Letra.cuerpo.copy(color = Color9, letterSpacing = 3.sp, fontSize = 16.sp))
            Text(prov.es, style = Letra.suave.copy(fontFamily = Mincho, color = K.textoTenue, textAlign = TextAlign.Center))
        }
    }
}

private val Color9 = androidx.compose.ui.graphics.Color(0xFFB09A72)

fun horasBonitas(h: Double): String {
    if (h < 1) return "${ceil(h * 60).toInt()} min"
    val medias = ceil(h * 2) / 2
    return if (medias % 1.0 == 0.0) "${medias.toInt()} h" else "${medias.toInt()} h y media"
}
