package io.github.sebastianbalbinu.kura.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sebastianbalbinu.kura.data.Estado
import io.github.sebastianbalbinu.kura.data.Oficio
import io.github.sebastianbalbinu.kura.data.PagoFijo
import io.github.sebastianbalbinu.kura.data.TipoOficio

@Composable
private fun Campo(valor: String, alCambiar: (String) -> Unit, etiqueta: String, numero: Boolean = false) {
    OutlinedTextField(
        value = valor, onValueChange = alCambiar, label = { Text(etiqueta) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numero) KeyboardType.Number else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = K.oro, unfocusedBorderColor = Color(0x33F3E7D3), focusedLabelColor = K.oro,
            cursorColor = K.oro, focusedTextColor = K.texto, unfocusedTextColor = K.texto, unfocusedLabelColor = K.textoTenue
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )
}

private fun numero(s: String) = s.filter { it.isDigit() }.toLongOrNull() ?: 0L

@Composable
private fun Dialogo(titulo: String, alGuardar: () -> Unit, alCerrar: () -> Unit, alBorrar: (() -> Unit)? = null, contenido: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        containerColor = Color(0xFF2A1F19),
        title = { Text(titulo, style = Letra.titulo.copy(fontSize = 20.sp)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { contenido() } },
        confirmButton = { TextButton(onClick = alGuardar) { Text("Guardar", color = K.oro) } },
        dismissButton = {
            if (alBorrar != null) TextButton(onClick = alBorrar) { Text("Borrar", color = K.bermellonClaro) }
            else TextButton(onClick = alCerrar) { Text("Cancelar", color = K.textoSuave) }
        }
    )
}

@Composable
fun DialogoPagoFijo(p: PagoFijo, nuevo: Boolean, alGuardar: (PagoFijo) -> Unit, alBorrar: () -> Unit, alCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(p.nombre) }
    var monto by remember { mutableStateOf(if (p.monto > 0) p.monto.toString() else "") }
    var dia by remember { mutableStateOf(p.dia.toString()) }
    Dialogo(if (nuevo) "Nuevo pago fijo" else "Pago fijo", {
        if (nombre.isNotBlank() && numero(monto) > 0)
            alGuardar(p.copy(nombre = nombre.trim(), monto = numero(monto), dia = numero(dia).toInt().coerceIn(1, 31)))
    }, alCerrar, if (nuevo) null else alBorrar) {
        Campo(nombre, { nombre = it }, "Qué es (arriendo, internet…)")
        Campo(monto, { monto = it }, "Cuánto, en pesos", true)
        Campo(dia, { dia = it }, "Qué día del mes", true)
    }
}

@Composable
fun DialogoCobro(e: Estado, alGuardar: (String, Long, String?) -> Unit, alCerrar: () -> Unit) {
    var cliente by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var oficio by remember { mutableStateOf<String?>(null) }
    Dialogo("Alguien le debe", {
        if (cliente.isNotBlank() && numero(monto) > 0) alGuardar(cliente.trim(), numero(monto), oficio)
    }, alCerrar) {
        Campo(cliente, { cliente = it }, "Quién")
        Campo(monto, { monto = it }, "Cuánto, en pesos", true)
        Text("De qué oficio", style = Letra.suave)
        Chips(e.oficios.map { it.id to "${it.emoji} ${it.nombre}" }, oficio) { oficio = it }
    }
}

@Composable
fun DialogoOficio(o: Oficio, nuevo: Boolean, alGuardar: (Oficio) -> Unit, alBorrar: () -> Unit, alCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(o.nombre) }
    var emoji by remember { mutableStateOf(o.emoji) }
    var tipo by remember { mutableStateOf(o.tipo) }
    var tarifa by remember { mutableStateOf(if (o.tarifaHora > 0) o.tarifaHora.toString() else "") }
    var pct by remember { mutableStateOf(o.pctNegocio.toString()) }
    var palabras by remember { mutableStateOf(o.palabras.joinToString(", ")) }
    var montoFijo by remember { mutableStateOf(if (o.montoFijo > 0) o.montoFijo.toString() else "") }
    var diaPago by remember { mutableStateOf(if (o.diaPago > 0) o.diaPago.toString() else "") }
    Dialogo(if (nuevo) "Nuevo oficio" else o.nombre, {
        if (nombre.isNotBlank()) alGuardar(o.copy(
            nombre = nombre.trim(), emoji = emoji.ifBlank { "✦" }, tipo = tipo, tarifaHora = numero(tarifa),
            pctNegocio = numero(pct).toInt().coerceIn(0, 90),
            palabras = palabras.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() },
            montoFijo = numero(montoFijo), diaPago = numero(diaPago).toInt().coerceIn(0, 31)
        ))
    }, alCerrar, if (nuevo) null else alBorrar) {
        Campo(nombre, { nombre = it }, "Nombre")
        Campo(emoji, { emoji = it }, "Emoji")
        Text("Cómo gana", style = Letra.suave)
        Chips(TipoOficio.entries.map { it.name to it.etiqueta }, tipo.name) { k -> k?.let { tipo = TipoOficio.valueOf(it) } }
        Campo(tarifa, { tarifa = it }, "Cuánto se hace por hora (aprox.)", true)
        Campo(pct, { pct = it }, "% de cada ingreso para el cofre del negocio", true)
        if (tipo == TipoOficio.FIJO) {
            Campo(montoFijo, { montoFijo = it }, "Cuánto le pagan", true)
            Campo(diaPago, { diaPago = it }, "Qué día le pagan", true)
        }
        Campo(palabras, { palabras = it }, "Palabras que lo identifican (separadas por coma)")
    }
}

@Composable
fun DialogoMonto(titulo: String, actual: Long, alGuardar: (Long) -> Unit, alCerrar: () -> Unit) {
    var v by remember { mutableStateOf(actual.toString()) }
    Dialogo(titulo, { alGuardar(numero(v)) }, alCerrar) { Campo(v, { v = it }, "En pesos", true) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(opciones: List<Pair<String, String>>, sel: String?, alElegir: (String?) -> Unit) {
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { (k, t) ->
            val s = k == sel
            Text(t, fontSize = 13.sp, color = if (s) K.oroClaro else K.textoSuave,
                modifier = Modifier
                    .background(if (s) Color(0x33C9A96E) else Color(0x0DF3E7D3), RoundedCornerShape(12.dp))
                    .border(1.dp, if (s) Color(0x99C9A96E) else Color(0x1AF3E7D3), RoundedCornerShape(12.dp))
                    .clickable { alElegir(if (s) null else k) }
                    .padding(horizontal = 11.dp, vertical = 7.dp))
        }
    }
}
