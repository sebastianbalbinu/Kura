package io.github.sebastianbalbinu.kura.engine

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

val ZONA: ZoneId = ZoneId.systemDefault()
val ES: Locale = Locale.forLanguageTag("es-CO")

/** $1.250.000 */
fun pesos(v: Long): String {
    val signo = if (v < 0) "−" else ""
    val s = kotlin.math.abs(v).toString().reversed().chunked(3).joinToString(".").reversed()
    return "$signo$$s"
}

/** $1,2M · $450k · $9.500 — para espacios pequeños */
fun pesosCorto(v: Long): String {
    val a = kotlin.math.abs(v)
    val signo = if (v < 0) "−" else ""
    return when {
        a >= 1_000_000 -> signo + "$" + String.format(ES, "%.1f", a / 1_000_000.0).removeSuffix(",0") + "M"
        a >= 100_000 -> signo + "$" + (a / 1000) + "k"
        else -> pesos(v)
    }
}

fun hoy(): LocalDate = LocalDate.now(ZONA)
fun fechaDe(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZONA).toLocalDate()
fun ahora(): Long = System.currentTimeMillis()
fun mesClave(d: LocalDate = hoy()) = "%04d-%02d".format(d.year, d.monthValue)

fun fechaLarga(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(TextStyle.FULL, ES) + " · " + d.dayOfMonth + " de " +
        d.month.getDisplayName(TextStyle.FULL, ES)

fun fechaCorta(d: LocalDate): String =
    d.dayOfMonth.toString() + " de " + d.month.getDisplayName(TextStyle.FULL, ES)

/** quita tildes y pasa a minúsculas: "Pagó Café" → "pago cafe" */
fun normal(s: String): String =
    Normalizer.normalize(s.lowercase(ES), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

fun redondearArriba(v: Long, paso: Long = 1000): Long = if (v <= 0) 0 else ((v + paso - 1) / paso) * paso
fun redondearAbajo(v: Long, paso: Long = 1000): Long = if (v <= 0) 0 else (v / paso) * paso
