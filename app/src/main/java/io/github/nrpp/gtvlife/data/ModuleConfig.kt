package io.github.nrpp.gtvlife.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Módulos extra que se pueden activar y desactivar desde la configuración. */
enum class ModuleId(val key: String, val title: String) {
    FORECAST("forecast", "Previsión 7 días"),
    AIR("air", "Calidad del aire"),
    POLLEN("pollen", "Polen"),
    MOON("moon", "Fase lunar"),
    WORLD_CLOCKS("worldclocks", "Relojes del mundo"),
    COUNTDOWN("countdown", "Cuenta atrás"),
    QUOTE("quote", "Frase del día"),
    ON_THIS_DAY("onthisday", "Tal día como hoy"),
    NEWS("news", "Noticias"),
    CRYPTO("crypto", "Criptomonedas"),
    CURRENCY("currency", "Divisas"),
    NOTES("notes", "Notas y tareas"),
    TIMER("timer", "Temporizador"),
    ELECTRICITY("electricity", "Precio de la luz"),
    MONTH("month", "Calendario del mes");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key }

        /** Activados de serie: los que funcionan sin configurar nada. */
        val DEFAULT_ENABLED = listOf(FORECAST, MOON, QUOTE, MONTH, TIMER).map { it.key }
    }
}

data class Countdown(val name: String, val date: LocalDate, val yearly: Boolean) {
    /** Próxima fecha del evento (si se repite cada año y ya pasó, la del año que viene). */
    fun next(today: LocalDate = LocalDate.now()): LocalDate {
        if (!yearly) return date
        var d = date.withYear(today.year)
        if (d.isBefore(today)) d = d.plusYears(1)
        return d
    }

    fun toJson() = JSONObject().put("name", name).put("date", date.toString()).put("yearly", yearly)

    companion object {
        fun fromJson(o: JSONObject): Countdown? {
            val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return null
            val name = o.optString("name").trim().take(40).ifEmpty { return null }
            return Countdown(name, date, o.optBoolean("yearly", false))
        }
    }
}

data class Note(val text: String, val done: Boolean) {
    fun toJson() = JSONObject().put("text", text).put("done", done)

    companion object {
        fun fromJson(o: JSONObject): Note? {
            val text = o.optString("text").trim().take(120).ifEmpty { return null }
            return Note(text, o.optBoolean("done", false))
        }
    }
}

internal object ModuleDefaults {
    val WORLD_CLOCKS = listOf("America/New_York", "Europe/London", "Asia/Tokyo")
    val NEWS_FEEDS = listOf("https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada")
    val CRYPTO = listOf("bitcoin", "ethereum")
    val CURRENCIES = listOf("USD", "GBP", "CHF", "JPY")
}

private val COIN_RE = Regex("^[a-z0-9-]{1,40}$")
private val CURRENCY_RE = Regex("^[A-Z]{3}$")

internal fun JSONObject.strings(key: String): List<String>? = optJSONArray(key)?.let { a ->
    (0 until a.length()).mapNotNull { a.optString(it, null)?.trim()?.takeIf(String::isNotEmpty) }
}

internal fun <T> JSONObject.objects(key: String, parse: (JSONObject) -> T?): List<T>? = optJSONArray(key)?.let { a ->
    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(parse) }
}

internal fun jsonArray(items: Iterable<Any>) = JSONArray().apply { items.forEach { put(it) } }

internal object ModuleValidation {
    fun modules(list: List<String>?) = list?.filter { ModuleId.from(it) != null }?.distinct() ?: ModuleId.DEFAULT_ENABLED
    fun zones(list: List<String>?) = (list ?: ModuleDefaults.WORLD_CLOCKS)
        .filter { runCatching { ZoneId.of(it) }.isSuccess }.distinct().take(8)
    fun feeds(list: List<String>?) = (list ?: ModuleDefaults.NEWS_FEEDS)
        .filter { it.startsWith("https://", true) || it.startsWith("http://", true) }.distinct().take(6)
    fun coins(list: List<String>?) = (list ?: ModuleDefaults.CRYPTO)
        .map { it.lowercase() }.filter { COIN_RE.matches(it) }.distinct().take(8)
    fun currencies(list: List<String>?) = (list ?: ModuleDefaults.CURRENCIES)
        .map { it.uppercase() }.filter { CURRENCY_RE.matches(it) && it != "EUR" }.distinct().take(8)
}
