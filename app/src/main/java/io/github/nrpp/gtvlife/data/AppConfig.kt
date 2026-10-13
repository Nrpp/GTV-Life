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
    val userName: String = "",
    val accent: String = "cyan",
    val clockStyle: String = "digital",
    val autoClockMinutes: Int = 0,
    val modules: List<String> = ModuleId.DEFAULT_ENABLED,
    val worldClocks: List<String> = ModuleDefaults.WORLD_CLOCKS,
    val countdowns: List<Countdown> = emptyList(),
    val newsFeeds: List<String> = ModuleDefaults.NEWS_FEEDS,
    val cryptoCoins: List<String> = ModuleDefaults.CRYPTO,
    val currencies: List<String> = ModuleDefaults.CURRENCIES,
    val notes: List<Note> = emptyList(),
    val electricityZone: String = "PCB",
) {
    val hasLocation get() = latitude != null && longitude != null

    fun isEnabled(m: ModuleId) = m.key in modules

    /** Módulos extra activados, en el orden del catálogo. */
    val enabledModules get() = ModuleId.entries.filter(::isEnabled)

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
        .put("userName", userName)
        .put("accent", accent)
        .put("clockStyle", clockStyle)
        .put("autoClockMinutes", autoClockMinutes)
        .put("modules", jsonArray(modules))
        .put("worldClocks", jsonArray(worldClocks))
        .put("countdowns", jsonArray(countdowns.map { it.toJson() }))
        .put("newsFeeds", jsonArray(newsFeeds))
        .put("cryptoCoins", jsonArray(cryptoCoins))
        .put("currencies", jsonArray(currencies))
        .put("notes", jsonArray(notes.map { it.toJson() }))
        .put("electricityZone", electricityZone)

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
                userName = o.optString("userName").trim().take(30),
                accent = o.optString("accent").takeIf { it in ACCENTS } ?: d.accent,
                clockStyle = o.optString("clockStyle").takeIf { it in CLOCK_STYLES } ?: d.clockStyle,
                autoClockMinutes = o.optInt("autoClockMinutes", d.autoClockMinutes).coerceIn(0, 120),
                modules = ModuleValidation.modules(o.strings("modules")),
                worldClocks = ModuleValidation.zones(o.strings("worldClocks")),
                countdowns = o.objects("countdowns", Countdown::fromJson)?.take(12) ?: emptyList(),
                newsFeeds = ModuleValidation.feeds(o.strings("newsFeeds")),
                cryptoCoins = ModuleValidation.coins(o.strings("cryptoCoins")),
                currencies = ModuleValidation.currencies(o.strings("currencies")),
                notes = o.objects("notes", Note::fromJson)?.take(30) ?: emptyList(),
                electricityZone = o.optString("electricityZone").takeIf { it == "PCB" || it == "CYM" } ?: d.electricityZone,
            )
        }

        val ACCENTS = setOf("cyan", "magenta", "ambar", "verde", "violeta")
        val CLOCK_STYLES = setOf("digital", "analog", "minimal")

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

    /**
     * Guarda la configuración. [invalidate] = false para cambios que no afectan a los datos
     * descargados (p. ej. marcar una tarea como hecha desde la TV), así no se recargan las cachés.
     */
    fun save(context: Context, config: AppConfig, invalidate: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, config.toJson().toString()).apply()
        if (invalidate) version++
        listeners.forEach { it(config) }
    }

    fun addListener(l: (AppConfig) -> Unit) = listeners.add(l)
    fun removeListener(l: (AppConfig) -> Unit) = listeners.remove(l)
}
