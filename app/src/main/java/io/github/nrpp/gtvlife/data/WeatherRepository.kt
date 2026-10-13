package io.github.nrpp.gtvlife.data

import io.github.nrpp.gtvlife.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class HourForecast(val time: LocalDateTime, val temp: Double, val precipProb: Int, val code: Int, val isDay: Boolean)

data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val precipProb: Int,
    val precipSum: Double,
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
)

data class Weather(
    val locationName: String,
    val temp: Double,
    val apparent: Double,
    val humidity: Int,
    val wind: Double,
    val code: Int,
    val isDay: Boolean,
    val now: LocalDateTime,
    val days: List<DayForecast>,
    val hourly: List<HourForecast>,
) {
    val today get() = days.firstOrNull()
    val tomorrow get() = days.getOrNull(1)

    /** Horas restantes de hoy (desde la hora actual). */
    fun remainingToday(): List<HourForecast> =
        hourly.filter { it.time.toLocalDate() == now.toLocalDate() && !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }

    fun next(hours: Int): List<HourForecast> =
        hourly.filter { !it.time.isBefore(now.withMinute(0).withSecond(0).withNano(0)) }.take(hours)

    /** Frase corta sobre la lluvia de hoy: "¿va a llover?". */
    fun rainSummary(): String {
        val rest = remainingToday()
        val rainy = rest.filter { it.precipProb >= 40 || WeatherCodes.isWet(it.code) }
        val maxProb = (rest.maxOfOrNull { it.precipProb } ?: 0).coerceAtLeast(0)
        if (rainy.isEmpty()) {
            return if (maxProb >= 20) "Poca probabilidad de lluvia (máx. $maxProb%)" else "No se espera lluvia hoy"
        }
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val from = rainy.first().time
        val to = rainy.last().time.plusHours(1)
        val what = if (rainy.any { WeatherCodes.isSnow(it.code) }) "Nieve" else "Lluvia"
        return if (from.toLocalTime() <= now.toLocalTime()) {
            "$what ahora y hasta las ${fmt.format(to)} (máx. $maxProb%)"
        } else {
            "$what probable entre las ${fmt.format(from)} y las ${fmt.format(to)} (máx. $maxProb%)"
        }
    }

    val willRainToday get() = remainingToday().any { it.precipProb >= 40 || WeatherCodes.isWet(it.code) }
}

object WeatherRepository {
    private const val TTL_MS = 20 * 60 * 1000L

    private val mutex = Mutex()
    private var cached: Weather? = null
    private var cachedAt = 0L
    private var cachedVersion = -1

    suspend fun get(config: AppConfig, force: Boolean = false): Weather = mutex.withLock {
        val fresh = cached != null && cachedVersion == ConfigStore.version &&
            System.currentTimeMillis() - cachedAt < TTL_MS
        if (fresh && !force) return@withLock cached!!
        val w = fetch(config)
        cached = w
        cachedAt = System.currentTimeMillis()
        cachedVersion = ConfigStore.version
        w
    }

    fun peek(): Weather? = cached

    private suspend fun fetch(config: AppConfig): Weather = withContext(Dispatchers.IO) {
        val lat = config.latitude ?: error("Ubicación no configurada")
        val lon = config.longitude ?: error("Ubicación no configurada")
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${"%.4f".format(Locale.US, lat)}&longitude=${"%.4f".format(Locale.US, lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,sunrise,sunset" +
            "&timezone=auto&forecast_days=3"
        parse(JSONObject(Http.getString(url)), config.locationName)
    }

    private fun parse(o: JSONObject, name: String): Weather {
        val cur = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val d = o.getJSONObject("daily")
        val hTimes = h.getJSONArray("time")
        val hourly = (0 until hTimes.length()).map { i ->
            HourForecast(
                time = LocalDateTime.parse(hTimes.getString(i)),
                temp = h.getJSONArray("temperature_2m").optDouble(i, 0.0),
                precipProb = h.getJSONArray("precipitation_probability").optInt(i, 0),
                code = h.getJSONArray("weather_code").optInt(i, 0),
                isDay = h.getJSONArray("is_day").optInt(i, 1) == 1,
            )
        }
        val dTimes = d.getJSONArray("time")
        val days = (0 until dTimes.length()).map { i ->
            DayForecast(
                date = LocalDate.parse(dTimes.getString(i)),
                code = d.getJSONArray("weather_code").optInt(i, 0),
                max = d.getJSONArray("temperature_2m_max").optDouble(i, 0.0),
                min = d.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                precipProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0),
                precipSum = d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                sunrise = d.getJSONArray("sunrise").optString(i).takeIf { it.isNotEmpty() }
                    ?.let { runCatching { LocalDateTime.parse(it).toLocalTime() }.getOrNull() },
                sunset = d.getJSONArray("sunset").optString(i).takeIf { it.isNotEmpty() }
                    ?.let { runCatching { LocalDateTime.parse(it).toLocalTime() }.getOrNull() },
            )
        }
        return Weather(
            locationName = name,
            temp = cur.optDouble("temperature_2m", 0.0),
            apparent = cur.optDouble("apparent_temperature", 0.0),
            humidity = cur.optInt("relative_humidity_2m", 0),
            wind = cur.optDouble("wind_speed_10m", 0.0),
            code = cur.optInt("weather_code", 0),
            isDay = cur.optInt("is_day", 1) == 1,
            now = LocalDateTime.parse(cur.getString("time")),
            days = days,
            hourly = hourly,
        )
    }

    /** Búsqueda de ciudades para la página de configuración. */
    fun geocode(query: String): JSONObject {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return JSONObject(Http.getString("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=es&format=json"))
    }
}

/** Códigos meteorológicos WMO usados por Open-Meteo. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nuboso"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55 -> "Llovizna"
        56, 57 -> "Llovizna helada"
        61 -> "Lluvia débil"
        63 -> "Lluvia"
        65 -> "Lluvia fuerte"
        66, 67 -> "Lluvia helada"
        71 -> "Nieve débil"
        73 -> "Nieve"
        75 -> "Nieve fuerte"
        77 -> "Granizo fino"
        80 -> "Chubascos débiles"
        81 -> "Chubascos"
        82 -> "Chubascos fuertes"
        85, 86 -> "Chubascos de nieve"
        95 -> "Tormenta"
        96, 99 -> "Tormenta con granizo"
        else -> "—"
    }

    fun emoji(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 80, 81 -> if (isDay) "🌦️" else "🌧️"
        56, 57, 61, 63, 65, 66, 67, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    fun isWet(code: Int) = code in 51..67 || code in 80..82 || code >= 95 || isSnow(code)
    fun isSnow(code: Int) = code in 71..77 || code in 85..86
}
