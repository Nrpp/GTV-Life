package io.github.nrpp.gtvlife.data

import android.os.SystemClock
import android.util.Xml
import io.github.nrpp.gtvlife.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

/**
 * Caché con caducidad. Si una recarga falla y hay datos anteriores para la misma clave,
 * se siguen mostrando los anteriores en lugar del error.
 */
class TtlCache<T : Any>(private val ttlMs: Long) {
    private val mutex = Mutex()
    private var value: T? = null
    private var key: Any? = null
    private var at = 0L

    val lastUpdated get() = at

    fun peek(key: Any): T? = value.takeIf { this.key == key }

    suspend fun get(key: Any, force: Boolean = false, load: suspend () -> T): T = mutex.withLock {
        val v = value
        if (!force && v != null && this.key == key && System.currentTimeMillis() - at < ttlMs) return@withLock v
        try {
            withContext(Dispatchers.IO) { load() }.also {
                value = it
                this.key = key
                at = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            if (v != null && this.key == key) v else throw e
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Calidad del aire y polen (Open-Meteo Air Quality)

data class AirQuality(
    val aqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val no2: Double?,
    /** Granos/m³ por tipo de polen; vacío si no hay datos para la zona (solo Europa). */
    val pollen: List<Pair<String, Double>>,
) {
    val level: Pair<String, Int>
        get() = when (aqi ?: -1) {
            in 0..19 -> "Buena" to 0xFF3DFFA2.toInt()
            in 20..39 -> "Razonable" to 0xFFB6F05A.toInt()
            in 40..59 -> "Moderada" to 0xFFFFD447.toInt()
            in 60..79 -> "Mala" to 0xFFFF9F43.toInt()
            in 80..99 -> "Muy mala" to 0xFFFF4D6D.toInt()
            -1 -> "Sin datos" to 0xFF8C9BBA.toInt()
            else -> "Extremadamente mala" to 0xFFB04DFF.toInt()
        }

    companion object {
        fun pollenLevel(v: Double): Pair<String, Int> = when {
            v < 1 -> "Nulo" to 0xFF4C5978.toInt()
            v < 15 -> "Bajo" to 0xFF3DFFA2.toInt()
            v < 50 -> "Moderado" to 0xFFFFD447.toInt()
            v < 200 -> "Alto" to 0xFFFF9F43.toInt()
            else -> "Muy alto" to 0xFFFF4D6D.toInt()
        }
    }
}

object AirRepository {
    private val cache = TtlCache<AirQuality>(30 * 60 * 1000L)
    private val POLLEN = listOf(
        "alder_pollen" to "Aliso", "birch_pollen" to "Abedul", "grass_pollen" to "Gramíneas",
        "mugwort_pollen" to "Artemisa", "olive_pollen" to "Olivo", "ragweed_pollen" to "Ambrosía",
    )

    fun peek(c: AppConfig) = cache.peek(c.latitude to c.longitude)

    suspend fun get(c: AppConfig): AirQuality {
        val lat = c.latitude ?: error("Configura tu ubicación")
        val lon = c.longitude ?: error("Configura tu ubicación")
        return cache.get(lat to lon) {
            val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${"%.4f".format(Locale.US, lat)}" +
                "&longitude=${"%.4f".format(Locale.US, lon)}&timezone=auto" +
                "&current=european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide," + POLLEN.joinToString(",") { it.first }
            val cur = JSONObject(Http.getString(url)).getJSONObject("current")
            fun d(k: String) = cur.optDouble(k).takeIf { !it.isNaN() }
            AirQuality(
                aqi = d("european_aqi")?.toInt(),
                pm25 = d("pm2_5"), pm10 = d("pm10"), ozone = d("ozone"), no2 = d("nitrogen_dioxide"),
                pollen = POLLEN.mapNotNull { (k, name) -> d(k)?.let { name to it } },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fase lunar (cálculo local, sin conexión)

data class MoonInfo(
    /** 0 = luna nueva, 0.5 = llena, 1 = nueva otra vez. */
    val phase: Double,
    val illumination: Double,
    val name: String,
    val ageDays: Double,
    val nextNew: LocalDate,
    val nextFull: LocalDate,
)

object Moon {
    private const val SYNODIC = 29.530588853
    private val REFERENCE_NEW_MOON = Instant.parse("2000-01-06T18:14:00Z")

    fun at(instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): MoonInfo {
        val days = (instant.toEpochMilli() - REFERENCE_NEW_MOON.toEpochMilli()) / 86_400_000.0
        val age = ((days % SYNODIC) + SYNODIC) % SYNODIC
        val phase = age / SYNODIC
        val illum = (1 - cos(2 * PI * phase)) / 2
        val name = when {
            age < 1.0 || age > SYNODIC - 1.0 -> "Luna nueva"
            phase < 0.235 -> "Luna creciente"
            phase < 0.265 -> "Cuarto creciente"
            phase < 0.485 -> "Gibosa creciente"
            phase < 0.515 -> "Luna llena"
            phase < 0.735 -> "Gibosa menguante"
            phase < 0.765 -> "Cuarto menguante"
            else -> "Luna menguante"
        }
        fun inDays(target: Double): LocalDate {
            var d = target - age
            if (d < 0.5) d += SYNODIC
            return instant.plusSeconds((d * 86400).toLong()).atZone(zone).toLocalDate()
        }
        return MoonInfo(phase, illum, name, age, inDays(SYNODIC), inDays(SYNODIC / 2))
    }
}

// ---------------------------------------------------------------------------------------------
// Relojes del mundo

object WorldCities {
    /** Ciudades sugeridas en la web de configuración (zona horaria -> nombre). */
    val NAMES = linkedMapOf(
        "Europe/Madrid" to "Madrid", "Atlantic/Canary" to "Canarias", "Europe/Lisbon" to "Lisboa",
        "Europe/London" to "Londres", "Europe/Paris" to "París", "Europe/Berlin" to "Berlín",
        "Europe/Rome" to "Roma", "Europe/Athens" to "Atenas", "Europe/Moscow" to "Moscú",
        "Africa/Cairo" to "El Cairo", "Africa/Johannesburg" to "Johannesburgo", "Asia/Dubai" to "Dubái",
        "Asia/Kolkata" to "Nueva Delhi", "Asia/Bangkok" to "Bangkok", "Asia/Shanghai" to "Pekín",
        "Asia/Hong_Kong" to "Hong Kong", "Asia/Singapore" to "Singapur", "Asia/Tokyo" to "Tokio",
        "Asia/Seoul" to "Seúl", "Australia/Sydney" to "Sídney", "Pacific/Auckland" to "Auckland",
        "Pacific/Honolulu" to "Honolulu", "America/Los_Angeles" to "Los Ángeles", "America/Denver" to "Denver",
        "America/Chicago" to "Chicago", "America/Mexico_City" to "Ciudad de México", "America/New_York" to "Nueva York",
        "America/Bogota" to "Bogotá", "America/Lima" to "Lima", "America/Caracas" to "Caracas",
        "America/Santiago" to "Santiago de Chile", "America/Argentina/Buenos_Aires" to "Buenos Aires",
        "America/Sao_Paulo" to "São Paulo", "America/Montevideo" to "Montevideo", "UTC" to "UTC",
    )

    fun name(zone: String) = NAMES[zone] ?: zone.substringAfterLast('/').replace('_', ' ')
}

// ---------------------------------------------------------------------------------------------
// Frase del día (sin conexión)

object Quotes {
    val ALL = listOf(
        "La vida es lo que pasa mientras estás ocupado haciendo otros planes." to "John Lennon",
        "El único modo de hacer un gran trabajo es amar lo que haces." to "Steve Jobs",
        "No cuentes los días, haz que los días cuenten." to "Muhammad Ali",
        "Caminante, no hay camino, se hace camino al andar." to "Antonio Machado",
        "La imaginación es más importante que el conocimiento." to "Albert Einstein",
        "El que lee mucho y anda mucho, ve mucho y sabe mucho." to "Miguel de Cervantes",
        "Lo que no te mata te hace más fuerte." to "Friedrich Nietzsche",
        "La felicidad no es algo hecho. Proviene de tus propias acciones." to "Dalái Lama",
        "Hay que tener paciencia: lo bueno tarda." to "Proverbio",
        "Si quieres ir rápido, camina solo; si quieres llegar lejos, camina acompañado." to "Proverbio africano",
        "El éxito es la suma de pequeños esfuerzos repetidos día tras día." to "Robert Collier",
        "Sé el cambio que quieres ver en el mundo." to "Mahatma Gandhi",
        "La mejor manera de predecir el futuro es crearlo." to "Peter Drucker",
        "Nunca es tarde para ser lo que podrías haber sido." to "George Eliot",
        "Donde una puerta se cierra, otra se abre." to "Miguel de Cervantes",
        "La simplicidad es la máxima sofisticación." to "Leonardo da Vinci",
        "Hoy es el primer día del resto de tu vida." to "Charles Dederich",
        "Lo importante no es lo que nos hace el destino, sino lo que nosotros hacemos de él." to "Florence Nightingale",
        "El tiempo es el mejor autor: siempre encuentra un final perfecto." to "Charles Chaplin",
        "No hay viento favorable para el que no sabe adónde va." to "Séneca",
        "Ser feliz no es tener lo que quieres, sino querer lo que tienes." to "Proverbio",
        "La paciencia es amarga, pero su fruto es dulce." to "Jean-Jacques Rousseau",
        "Haz hoy lo que otros no quieren y mañana vivirás como otros no pueden." to "Jerry Rice",
        "Quien tiene un porqué para vivir, encontrará casi siempre el cómo." to "Friedrich Nietzsche",
        "Solo sé que no sé nada." to "Sócrates",
        "El arte de vencer se aprende en las derrotas." to "Simón Bolívar",
        "La creatividad es la inteligencia divirtiéndose." to "Albert Einstein",
        "Todo parece imposible hasta que se hace." to "Nelson Mandela",
        "Vive como si fueras a morir mañana; aprende como si fueras a vivir siempre." to "Mahatma Gandhi",
        "El optimismo es la fe que conduce al logro." to "Helen Keller",
        "Un viaje de mil millas comienza con un solo paso." to "Lao-Tse",
        "La suerte es lo que sucede cuando la preparación se encuentra con la oportunidad." to "Séneca",
        "Pon tu corazón, mente y alma incluso en los actos más pequeños." to "Swami Sivananda",
        "La disciplina es el puente entre las metas y los logros." to "Jim Rohn",
        "Los sueños no funcionan a menos que tú lo hagas." to "John C. Maxwell",
        "Lo que haces hoy puede mejorar todos tus mañanas." to "Ralph Marston",
        "Nadie es tan sabio que no tenga algo que aprender." to "Baltasar Gracián",
        "Lo bueno, si breve, dos veces bueno." to "Baltasar Gracián",
        "Elige un trabajo que te guste y no tendrás que trabajar ni un día de tu vida." to "Confucio",
        "Aprender sin pensar es esfuerzo perdido." to "Confucio",
        "La risa es el sol que ahuyenta el invierno del rostro humano." to "Victor Hugo",
        "No dejes para mañana lo que puedas hacer hoy." to "Benjamin Franklin",
        "El mundo es un libro y quienes no viajan leen solo una página." to "San Agustín",
        "La gratitud convierte lo que tenemos en suficiente." to "Proverbio",
        "Cada día es una nueva oportunidad para cambiar tu vida." to "Proverbio",
    )

    fun of(day: LocalDate = LocalDate.now()): Pair<String, String> = ALL[(day.toEpochDay() % ALL.size).toInt()]
}

// ---------------------------------------------------------------------------------------------
// Tal día como hoy (Wikipedia en español)

data class HistoryEvent(val year: Int, val text: String)

object OnThisDayRepository {
    private val cache = TtlCache<List<HistoryEvent>>(12 * 60 * 60 * 1000L)

    fun peek() = cache.peek(LocalDate.now())

    suspend fun get(): List<HistoryEvent> {
        val day = LocalDate.now()
        return cache.get(day) {
            val url = "https://es.wikipedia.org/api/rest_v1/feed/onthisday/selected/%02d/%02d".format(day.monthValue, day.dayOfMonth)
            val arr = JSONObject(Http.getString(url, mapOf("Accept" to "application/json"))).getJSONArray("selected")
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .map { HistoryEvent(it.optInt("year"), it.optString("text").trim()) }
                .filter { it.text.isNotEmpty() }
                .sortedByDescending { it.year }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Noticias (RSS / Atom)

data class NewsItem(val title: String, val summary: String, val source: String, val date: Instant?)

object NewsRepository {
    private val cache = TtlCache<List<NewsItem>>(20 * 60 * 1000L)

    fun peek(c: AppConfig) = cache.peek(c.newsFeeds)

    suspend fun get(c: AppConfig): List<NewsItem> {
        if (c.newsFeeds.isEmpty()) error("Añade algún feed RSS desde la configuración")
        return cache.get(c.newsFeeds) {
            val results = coroutineScope {
                c.newsFeeds.map { url -> async { runCatching { parse(Http.getString(url)) } } }.awaitAll()
            }
            if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
            results.flatMap { it.getOrDefault(emptyList()) }
                .sortedByDescending { it.date ?: Instant.EPOCH }
                .distinctBy { it.title }
                .take(40)
        }
    }

    internal fun parse(xml: String): List<NewsItem> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val out = mutableListOf<NewsItem>()
        var source = ""
        var inItem = false
        var title = ""
        var summary = ""
        var date: Instant? = null
        var tag = ""
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name.lowercase()
                    if (tag == "item" || tag == "entry") {
                        inItem = true; title = ""; summary = ""; date = null
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    val t = p.text ?: ""
                    if (t.isBlank()) continue
                    if (!inItem && tag == "title" && source.isEmpty()) source = clean(t)
                    if (inItem) when (tag) {
                        "title" -> title += t
                        "description", "summary", "content:encoded" -> if (summary.length < 400) summary += t
                        "pubdate", "published", "updated", "dc:date" -> if (date == null) date = parseDate(t.trim())
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = p.name.lowercase()
                    if ((name == "item" || name == "entry") && inItem) {
                        inItem = false
                        if (title.isNotBlank()) out += NewsItem(clean(title), clean(summary).take(280), source.substringBefore(":").trim(), date)
                    }
                    tag = ""
                }
            }
        }
        return out
    }

    private fun clean(s: String) = s.replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim()

    private fun parseDate(s: String): Instant? =
        runCatching { ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(s) }.getOrNull()
}

// ---------------------------------------------------------------------------------------------
// Criptomonedas (CoinGecko)

data class Coin(val id: String, val symbol: String, val name: String, val price: Double, val change24h: Double?, val change7d: Double?, val sparkline: List<Double>)

object CryptoRepository {
    private val cache = TtlCache<List<Coin>>(10 * 60 * 1000L)

    fun peek(c: AppConfig) = cache.peek(c.cryptoCoins)

    suspend fun get(c: AppConfig): List<Coin> {
        if (c.cryptoCoins.isEmpty()) error("Elige alguna criptomoneda en la configuración")
        return cache.get(c.cryptoCoins) {
            val ids = URLEncoder.encode(c.cryptoCoins.joinToString(","), "UTF-8")
            val arr = org.json.JSONArray(
                Http.getString("https://api.coingecko.com/api/v3/coins/markets?vs_currency=eur&ids=$ids&sparkline=true&price_change_percentage=24h,7d"),
            )
            val coins = (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                val spark = o.optJSONObject("sparkline_in_7d")?.optJSONArray("price")
                Coin(
                    id = o.getString("id"), symbol = o.optString("symbol").uppercase(), name = o.optString("name"),
                    price = o.optDouble("current_price"),
                    change24h = o.optDouble("price_change_percentage_24h_in_currency").takeIf { !it.isNaN() },
                    change7d = o.optDouble("price_change_percentage_7d_in_currency").takeIf { !it.isNaN() },
                    sparkline = spark?.let { s -> (0 until s.length()).map { s.optDouble(it) }.filter { !it.isNaN() } } ?: emptyList(),
                )
            }
            coins.sortedBy { c.cryptoCoins.indexOf(it.id) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Divisas (Frankfurter, tipos del BCE)

data class Rate(val code: String, val rate: Double, val change: Double?, val history: List<Double>)

object CurrencyRepository {
    private val cache = TtlCache<List<Rate>>(60 * 60 * 1000L)

    fun peek(c: AppConfig) = cache.peek(c.currencies)

    suspend fun get(c: AppConfig): List<Rate> {
        if (c.currencies.isEmpty()) error("Elige alguna divisa en la configuración")
        return cache.get(c.currencies) {
            val from = LocalDate.now().minusDays(30)
            val json = JSONObject(Http.getString("https://api.frankfurter.dev/v1/$from..?base=EUR&symbols=${c.currencies.joinToString(",")}"))
            val rates = json.getJSONObject("rates")
            val days = rates.keys().asSequence().sorted().toList()
            c.currencies.mapNotNull { code ->
                val series = days.mapNotNull { d -> rates.getJSONObject(d).optDouble(code).takeIf { !it.isNaN() } }
                if (series.isEmpty()) return@mapNotNull null
                val last = series.last()
                val prev = series.getOrNull(series.size - 2)
                Rate(code, last, prev?.let { (last - it) / it * 100 }, series)
            }
        }
    }

    val NAMES = mapOf(
        "USD" to "Dólar estadounidense", "GBP" to "Libra esterlina", "CHF" to "Franco suizo", "JPY" to "Yen japonés",
        "CNY" to "Yuan chino", "MXN" to "Peso mexicano", "BRL" to "Real brasileño", "CAD" to "Dólar canadiense",
        "AUD" to "Dólar australiano", "SEK" to "Corona sueca", "NOK" to "Corona noruega", "DKK" to "Corona danesa",
        "PLN" to "Esloti polaco", "TRY" to "Lira turca", "INR" to "Rupia india", "KRW" to "Won surcoreano",
    )
}

// ---------------------------------------------------------------------------------------------
// Precio de la luz (PVPC, Red Eléctrica)

data class HourPrice(val hour: Int, val eurKwh: Double)
data class ElectricityDay(val date: LocalDate, val prices: List<HourPrice>) {
    fun at(hour: Int) = prices.firstOrNull { it.hour == hour }
    val cheapest get() = prices.minByOrNull { it.eurKwh }
    val priciest get() = prices.maxByOrNull { it.eurKwh }
    val average get() = prices.map { it.eurKwh }.average()

    /** Color según el tercio en que cae el precio del día: verde, amarillo o rojo. */
    fun color(p: Double): Int {
        val sorted = prices.map { it.eurKwh }.sorted()
        if (sorted.isEmpty()) return 0xFF8C9BBA.toInt()
        val t1 = sorted[sorted.size / 3]
        val t2 = sorted[sorted.size * 2 / 3]
        return when {
            p <= t1 -> 0xFF3DFFA2.toInt()
            p <= t2 -> 0xFFFFD447.toInt()
            else -> 0xFFFF4D6D.toInt()
        }
    }
}

object ElectricityRepository {
    private val cache = TtlCache<ElectricityDay>(60 * 60 * 1000L)

    fun peek(c: AppConfig) = cache.peek(c.electricityZone to LocalDate.now())

    suspend fun get(c: AppConfig): ElectricityDay {
        val day = LocalDate.now()
        return cache.get(c.electricityZone to day) {
            runCatching { fromEsios(day, c.electricityZone) }.getOrElse { first ->
                runCatching { fromApiDatos(day) }.getOrElse { throw IllegalStateException("Red Eléctrica no responde (${first.message})") }
            }
        }
    }

    private fun num(s: String) = s.replace(".", "").replace(",", ".").toDouble()

    private fun fromEsios(day: LocalDate, zone: String): ElectricityDay {
        val json = JSONObject(Http.getString("https://api.esios.ree.es/archives/70/download_json?locale=es&date=$day", mapOf("Accept" to "application/json")))
        val arr = json.getJSONArray("PVPC")
        val prices = (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            HourPrice(o.getString("Hora").substringBefore("-").toInt(), num(o.getString(zone)) / 1000.0)
        }
        require(prices.isNotEmpty()) { "Sin precios" }
        return ElectricityDay(day, prices)
    }

    private fun fromApiDatos(day: LocalDate): ElectricityDay {
        val url = "https://apidatos.ree.es/es/datos/mercados/precios-mercados-tiempo-real?start_date=${day}T00:00&end_date=${day}T23:59&time_trunc=hour"
        val included = JSONObject(Http.getString(url, mapOf("Accept" to "application/json"))).getJSONArray("included")
        val pvpc = (0 until included.length()).map { included.getJSONObject(it) }
            .firstOrNull { it.optString("type").contains("PVPC", true) || it.optString("id") == "1001" }
            ?: error("Sin datos de PVPC")
        val values = pvpc.getJSONObject("attributes").getJSONArray("values")
        val prices = (0 until values.length()).map { values.getJSONObject(it) }.map { v ->
            HourPrice(OffsetDateTime.parse(v.getString("datetime")).hour, v.getDouble("value") / 1000.0)
        }.distinctBy { it.hour }
        return ElectricityDay(day, prices)
    }
}

// ---------------------------------------------------------------------------------------------
// Temporizador y cronómetro (estado global mientras la app esté viva)

object TimerState {
    enum class Mode { TIMER, STOPWATCH }

    var mode = Mode.TIMER
        private set

    /** Temporizador: duración configurada y momento (uptime) en que termina si está en marcha. */
    var timerSetMs = 5 * 60_000L
        private set
    private var timerEndAt = 0L
    private var timerRemainingPaused = 5 * 60_000L

    /** Cronómetro: tiempo acumulado y momento en que se reanudó. */
    private var swAccumulated = 0L
    private var swStartedAt = 0L

    var running = false
        private set

    /** El temporizador llegó a cero y aún no se ha avisado/descartado. */
    var ringing = false
        private set

    private fun now() = SystemClock.elapsedRealtime()

    fun switchMode(m: Mode) {
        if (m == mode) return
        pause()
        mode = m
    }

    fun add(ms: Long) {
        if (mode != Mode.TIMER) return
        if (running) {
            timerEndAt += ms
        } else {
            timerRemainingPaused = (timerRemainingPaused + ms).coerceIn(0, 24 * 3_600_000L)
            timerSetMs = timerRemainingPaused
        }
    }

    fun toggle() = if (running) pause() else start()

    fun start() {
        if (running) return
        when (mode) {
            Mode.TIMER -> {
                if (timerRemainingPaused <= 0) return
                timerEndAt = now() + timerRemainingPaused
            }
            Mode.STOPWATCH -> swStartedAt = now()
        }
        running = true
        ringing = false
    }

    fun pause() {
        if (!running) return
        when (mode) {
            Mode.TIMER -> timerRemainingPaused = (timerEndAt - now()).coerceAtLeast(0)
            Mode.STOPWATCH -> swAccumulated += now() - swStartedAt
        }
        running = false
    }

    fun reset() {
        running = false
        ringing = false
        when (mode) {
            Mode.TIMER -> timerRemainingPaused = timerSetMs
            Mode.STOPWATCH -> swAccumulated = 0
        }
    }

    /** Milisegundos a mostrar: restantes (temporizador) o transcurridos (cronómetro). */
    fun displayMs(): Long = when (mode) {
        Mode.TIMER -> if (running) (timerEndAt - now()).coerceAtLeast(0) else timerRemainingPaused
        Mode.STOPWATCH -> swAccumulated + if (running) now() - swStartedAt else 0
    }

    /** Comprueba si el temporizador acaba de terminar. Devuelve true una sola vez. */
    fun checkFinished(): Boolean {
        if (mode == Mode.TIMER && running && now() >= timerEndAt) {
            running = false
            ringing = true
            timerRemainingPaused = timerSetMs
            return true
        }
        return false
    }

    fun dismiss() { ringing = false }

    val isActive get() = running || (mode == Mode.STOPWATCH && swAccumulated > 0)

    fun format(ms: Long): String {
        val totalS = ms / 1000
        val h = totalS / 3600
        val m = (totalS % 3600) / 60
        val s = totalS % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

/** Día de la semana corto en español ("LUN", "MAR"...). */
fun shortWeekday(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("es-ES")).uppercase().removeSuffix(".")
