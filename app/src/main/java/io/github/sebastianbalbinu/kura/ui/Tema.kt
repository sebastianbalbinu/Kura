package io.github.sebastianbalbinu.kura.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.R

/** Paleta "té hojicha": marrón de té tostado, hoja de oro apagada y bermellón de sello. */
object K {
    val fondoArriba = Color(0xFF2E231C)
    val fondoAbajo = Color(0xFF1A1310)
    val niebla = Color(0xFF7A5233)
    val texto = Color(0xFFEFE6D6)
    val textoSuave = Color(0xFFB3A593)
    val textoTenue = Color(0xFF8A7B6B)
    val oro = Color(0xFFC9A96E)
    val oroClaro = Color(0xFFE6CE9C)
    val oroOscuro = Color(0xFF9C7B45)
    val bermellon = Color(0xFFB8432B)
    val bermellonClaro = Color(0xFFE6A38E)
    val papel = Color(0xFFF4E6D6)
    val vidrio = Color(0x14F3E7D3)
    val vidrioBorde = Color(0x1AF3E7D3)
    val verde = Color(0xFFA9C4A0)
}

val Mincho = FontFamily(Font(R.font.lora, FontWeight.Normal), Font(R.font.lora, FontWeight.Medium))

object Letra {
    val numeroGrande = TextStyle(fontFamily = Mincho, fontSize = 44.sp, color = K.texto, fontWeight = FontWeight.Medium)
    val titulo = TextStyle(fontFamily = Mincho, fontSize = 26.sp, color = K.texto, fontWeight = FontWeight.Medium)
    val numero = TextStyle(fontFamily = Mincho, fontSize = 18.sp, color = K.texto)
    val cuerpo = TextStyle(fontSize = 15.sp, color = K.texto, lineHeight = 22.sp)
    val suave = TextStyle(fontSize = 13.sp, color = K.textoSuave, lineHeight = 18.sp)
    val etiqueta = TextStyle(fontSize = 11.sp, color = K.textoTenue, letterSpacing = 2.sp)
}

@Composable
fun TemaKura(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = K.oro, onPrimary = Color(0xFF1A130A), background = K.fondoAbajo, surface = Color(0xFF2A1F19),
            onSurface = K.texto, onBackground = K.texto, surfaceVariant = Color(0xFF34271F), outline = K.vidrioBorde,
            secondary = K.bermellon, surfaceContainerHigh = Color(0xFF2F241D)
        ),
        content = contenido
    )
}
