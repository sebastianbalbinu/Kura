package io.github.sebastianbalbinu.kura.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.sin

/** Vidrio: translúcido, con un filo de luz arriba. Sin desenfoque real para que todo vaya fluido. */
fun Modifier.vidrio(radio: Dp = 22.dp, borde: Color = K.vidrioBorde) = this
    .clip(RoundedCornerShape(radio))
    .background(Brush.linearGradient(listOf(Color(0x1CF3E7D3), Color(0x08F3E7D3))))
    .border(1.dp, borde, RoundedCornerShape(radio))

@Composable
fun Hanko(kanji: String, tam: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(tam)
            .rotate(-4f)
            .clip(RoundedCornerShape(tam * 0.22f))
            .background(K.bermellon)
            .border(1.5.dp, Color(0x40FFEBDC), RoundedCornerShape(tam * 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        Text(kanji, color = K.papel, fontSize = (tam.value * 0.52f).sp, fontWeight = FontWeight.Bold, fontFamily = Mincho)
    }
}

/** El ensō: un círculo de pincel que se va cerrando con el progreso. */
@Composable
fun Enso(progreso: Float, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progreso) {
        anim.animateTo(progreso.coerceIn(0f, 1f), tween(1400, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val grosor = size.minDimension * 0.045f
            val r = size.minDimension / 2 - grosor * 1.5f
            val tl = Offset(center.x - r, center.y - r)
            val sz = Size(r * 2, r * 2)
            // huella tenue del círculo completo
            drawArc(K.oro.copy(alpha = 0.10f), -90f, 330f, false, tl, sz, style = Stroke(grosor * 0.8f, cap = StrokeCap.Round))
            val barrido = 330f * anim.value
            if (barrido > 0.5f) {
                // trazo principal
                drawArc(K.oro, -90f, barrido, false, tl, sz, style = Stroke(grosor, cap = StrokeCap.Round))
                // cerdas del pincel: arcos finos ligeramente desplazados
                for (i in 0 until 5) {
                    val dr = (sin(i * 2.1f) * grosor * 0.38f)
                    val a = 0.18f + 0.06f * i
                    drawArc(
                        if (i % 2 == 0) K.oroClaro.copy(alpha = a) else K.oroOscuro.copy(alpha = a),
                        -90f + i * 1.3f, (barrido - i * 4f).coerceAtLeast(0f), false,
                        Offset(tl.x - dr, tl.y - dr), Size(sz.width + dr * 2, sz.height + dr * 2),
                        style = Stroke(grosor * (0.18f + 0.05f * i), cap = StrokeCap.Round)
                    )
                }
            }
        }
        contenido()
    }
}

@Composable
fun TarjetaKenzo(texto: String, modifier: Modifier = Modifier, alTocar: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .vidrio()
            .let { if (alTocar != null) it.clickable { alTocar() } else it }
            .padding(16.dp)
    ) {
        Hanko("K", 30.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("KENZŌ", style = Letra.etiqueta.copy(color = K.oro))
            Spacer(Modifier.height(4.dp))
            Text(texto, style = Letra.cuerpo.copy(color = Color(0xFFE2D8C6)))
        }
    }
}

@Composable
fun BotonDorado(texto: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFDCC18C), K.oroOscuro)))
            .clickable { alTocar() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(texto, color = Color(0xFF1A130A), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

@Composable
fun BotonSuave(texto: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x26F3E7D3), RoundedCornerShape(14.dp))
            .clickable { alTocar() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(texto, color = K.texto, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

/** La barra de abajo para escribirle o mandarle audios a Kenzō. */
@Composable
fun BarraKenzo(alEnviar: (String) -> Unit, alMicrofono: () -> Unit, modifier: Modifier = Modifier) {
    var texto by remember { mutableStateOf("") }
    Row(
        modifier
            .fillMaxWidth()
            .vidrio(30.dp)
            .background(Color(0x22140E0A))
            .padding(start = 20.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            if (texto.isEmpty()) Text("Dígale a Kenzō…", color = K.textoTenue, fontSize = 15.sp)
            BasicTextField(
                value = texto, onValueChange = { texto = it },
                textStyle = TextStyle(color = K.texto, fontSize = 15.sp),
                cursorBrush = SolidColor(K.oro),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { alEnviar(texto); texto = "" }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFDCC18C), K.oroOscuro)))
                .clickable { if (texto.isBlank()) alMicrofono() else { alEnviar(texto); texto = "" } },
            contentAlignment = Alignment.Center
        ) {
            Text(if (texto.isBlank()) "🎙" else "↑", fontSize = if (texto.isBlank()) 18.sp else 22.sp,
                color = Color(0xFF1A130A), fontWeight = FontWeight.Bold)
        }
    }
}

/** Barra fina que se llena "como arroz cayendo". */
@Composable
fun BarraArroz(frac: Float, modifier: Modifier = Modifier, color: Color = K.oro) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(frac) { anim.animateTo(frac.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing)) }
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x12F3E7D3))) {
        Box(
            Modifier.fillMaxWidth(anim.value).height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(K.oroOscuro, color)))
        )
    }
}

@Composable
fun Icono(k: String, color: Color = K.oro, tam: Dp = 34.dp) {
    Box(
        Modifier.size(tam).clip(RoundedCornerShape(10.dp)).border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) { Text(k, fontSize = (tam.value * 0.48f).sp) }
}
