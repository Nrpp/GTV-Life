package io.github.nrpp.gtvlife.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

data class CalendarSource(
    val name: String,
    val url: String,
    val color: String,
) {
    fun toJson() = JSONObject().put("name", name).put("url", url).put("color", color)

    companion object {
        fun fromJson(o: JSONObject) = CalendarSource(
            name = o.optString("name").trim().take(60),
            url = o.optString("url").trim(),
            color = o.optString("color").takeIf { COLOR_RE.matches(it) } ?: "#4FC3F7",
        )
    }
}

data class AppConfig(
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val calendars: List<CalendarSource> = emptyList(),
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val dimEnabled: Boolean = false,
    val dimLevel: Int = 60,
    val dimNightOnly: Boolean = false,
    val nightStart: String = "22:00",
    val nightEnd: String = "07:00",
    val showClock: Boolean = true,
    val showCalendar: Boolean = true,
    val showWeather: Boolean = true,
) {
    val hasLocation get() = latitude != null && longitude != null

    fun toJson(): JSONObject = JSONObject()
        .put("locationName", locationName)
        .put("latitude", latitude ?: JSONObject.NULL)
        .put("longitude", longitude ?: JSONObject.NULL)
        .put("calendars", JSONArray().apply { calendars.forEach { put(it.toJson()) } })
        .put("use24h", use24h)
        .put("showSeconds", showSeconds)
        .put("dimEnabled", dimEnabled)
        .put("dimLevel", dimLevel)
        .put("dimNightOnly", dimNightOnly)
        .put("nightStart", nightStart)
        .put("nightEnd", nightEnd)
        .put("showClock", showClock)
        .put("showCalendar", showCalendar)
        .put("showWeather", showWeather)

    companion object {
        /** Construye una configuración a partir de JSON, validando y saneando cada campo. */
        fun fromJson(o: JSONObject): AppConfig {
            val d = AppConfig()
            val lat = o.optDouble("latitude").takeIf { !it.isNaN() && it in -90.0..90.0 }
            val lon = o.optDouble("longitude").takeIf { !it.isNaN() && it in -180.0..180.0 }
            val cals = o.optJSONArray("calendars")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(CalendarSource::fromJson) }
            }.orEmpty().filter { isValidCalendarUrl(it.url) }.take(20)
            return AppConfig(
                locationName = o.optString("locationName").trim().take(120),
                latitude = if (lat != null && lon != null) lat else null,
                longitude = if (lat != null && lon != null) lon else null,
                calendars = cals,
                use24h = o.optBoolean("use24h", d.use24h),
                showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                dimEnabled = o.optBoolean("dimEnabled", d.dimEnabled),
                dimLevel = o.optInt("dimLevel", d.dimLevel).coerceIn(10, 95),
                dimNightOnly = o.optBoolean("dimNightOnly", d.dimNightOnly),
                nightStart = o.optString("nightStart").takeIf { TIME_RE.matches(it) } ?: d.nightStart,
                nightEnd = o.optString("nightEnd").takeIf { TIME_RE.matches(it) } ?: d.nightEnd,
                showClock = o.optBoolean("showClock", d.showClock),
                showCalendar = o.optBoolean("showCalendar", d.showCalendar),
                showWeather = o.optBoolean("showWeather", d.showWeather),
            )
        }

        fun isValidCalendarUrl(url: String): Boolean {
            val u = url.lowercase()
            return u.startsWith("https://") || u.startsWith("http://") || u.startsWith("webcal://")
        }
    }
}

private val COLOR_RE = Regex("^#[0-9a-fA-F]{6}$")
private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

/** Persistencia de la configuración en SharedPreferences y aviso de cambios. */
object ConfigStore {
    private const val PREFS = "gtvlife"
    private const val KEY = "config"

    private val listeners = CopyOnWriteArrayList<(AppConfig) -> Unit>()

    @Volatile
    var version: Int = 0
        private set

    fun isConfigured(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun load(context: Context): AppConfig {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return AppConfig()
        return runCatching { AppConfig.fromJson(JSONObject(raw)) }.getOrDefault(AppConfig())
    }

    fun save(context: Context, config: AppConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, config.toJson().toString()).apply()
        version++
        listeners.forEach { it(config) }
    }

    fun addListener(l: (AppConfig) -> Unit) = listeners.add(l)
    fun removeListener(l: (AppConfig) -> Unit) = listeners.remove(l)
}
