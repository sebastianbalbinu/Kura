package io.github.sebastianbalbinu.kura

import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import io.github.sebastianbalbinu.kura.ui.BarraKenzo
import io.github.sebastianbalbinu.kura.ui.K
import io.github.sebastianbalbinu.kura.ui.PantallaAjustes
import io.github.sebastianbalbinu.kura.ui.PantallaBodega
import io.github.sebastianbalbinu.kura.ui.PantallaInicio
import io.github.sebastianbalbinu.kura.ui.PantallaKenzo
import io.github.sebastianbalbinu.kura.ui.PantallaOficios
import io.github.sebastianbalbinu.kura.ui.TemaKura
import io.github.sebastianbalbinu.kura.ui.vidrio

enum class Pantalla(val kanji: String, val nombre: String) {
    INICIO("蔵", "Inicio"), BODEGA("箱", "Bodega"), OFICIOS("職", "Oficios"), KENZO("賢", "Kenzō"), AJUSTES("設", "Ajustes")
}

class MainActivity : ComponentActivity() {
    private val vm: KuraViewModel by viewModels()
    private var sonidos: SoundPool? = null
    private var campana = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sonidos = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build().also { campana = it.load(this, R.raw.campana, 1) }

        setContent { TemaKura { App(vm, alSonar = { if (vm.estado.value.ajustes.sonido) sonidos?.play(campana, 0.55f, 0.55f, 1, 0, 1f) }) } }
    }

    override fun onDestroy() {
        sonidos?.release(); sonidos = null
        super.onDestroy()
    }
}

@Composable
fun App(vm: KuraViewModel, alSonar: () -> Unit) {
    val e by vm.estado.collectAsState()
    val actualizacion by vm.actualizador.estado.collectAsState()
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.INICIO) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.campana.collect { alSonar() } }
    LaunchedEffect(Unit) {
        vm.compartir.collect { t ->
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t).setPackage("com.whatsapp")
            try { ctx.startActivity(i) } catch (_: Exception) {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Enviar"))
            }
        }
    }

    val voz = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val texto = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!texto.isNullOrBlank()) { vm.enviar(texto); pantalla = Pantalla.KENZO }
    }
    val escuchar = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dígale a Kenzō…")
        try { voz.launch(i) } catch (_: Exception) {
            Toast.makeText(ctx, "Este teléfono no tiene dictado por voz instalado", Toast.LENGTH_LONG).show()
        }
    }

    BackHandler(enabled = pantalla != Pantalla.INICIO) { pantalla = Pantalla.INICIO }

    Fondo {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(pantalla, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }, label = "pantalla") { p ->
                    when (p) {
                        Pantalla.INICIO -> PantallaInicio(e, alAjustes = { pantalla = Pantalla.AJUSTES }, alKenzo = { pantalla = Pantalla.KENZO },
                            actualizacion = actualizacion, alActualizar = { vm.actualizar() })
                        Pantalla.BODEGA -> PantallaBodega(e, vm)
                        Pantalla.OFICIOS -> PantallaOficios(e, vm)
                        Pantalla.KENZO -> PantallaKenzo(e, vm)
                        Pantalla.AJUSTES -> PantallaAjustes(e, vm) { pantalla = Pantalla.INICIO }
                    }
                }
            }
            if (pantalla == Pantalla.INICIO || pantalla == Pantalla.KENZO) {
                BarraKenzo(
                    alEnviar = { t -> if (t.isNotBlank()) { vm.enviar(t); pantalla = Pantalla.KENZO } },
                    alMicrofono = escuchar,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            Navegacion(pantalla) { pantalla = it }
        }
    }
}

@Composable
private fun Navegacion(actual: Pantalla, alElegir: (Pantalla) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).vidrio(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf(Pantalla.INICIO, Pantalla.BODEGA, Pantalla.OFICIOS, Pantalla.KENZO).forEach { p ->
            val sel = p == actual
            Column(
                Modifier.weight(1f)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { alElegir(p) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(p.kanji, fontSize = 19.sp, color = if (sel) K.oro else K.textoTenue)
                Text(p.nombre, fontSize = 10.5.sp, color = if (sel) K.oroClaro else K.textoTenue)
            }
        }
    }
}

/** Fondo té hojicha liso con niebla cálida. */
@Composable
private fun Fondo(contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(K.fondoArriba, K.fondoAbajo)))) {
        Box(Modifier.offset((-40).dp, 120.dp).size(320.dp).clip(CircleShape).background(
            Brush.radialGradient(listOf(K.niebla.copy(alpha = 0.32f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomEnd).offset(80.dp, (-60).dp).size(300.dp).clip(CircleShape).background(
            Brush.radialGradient(listOf(Color(0xFF5A4030).copy(alpha = 0.25f), Color.Transparent))))
        contenido()
    }
}
