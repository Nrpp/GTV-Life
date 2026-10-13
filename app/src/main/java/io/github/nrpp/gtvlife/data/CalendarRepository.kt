package io.github.nrpp.gtvlife.data

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import io.github.nrpp.gtvlife.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

data class CalEvent(
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val location: String?,
    val calendarName: String,
    val color: String,
)

data class CalendarResult(
    val events: List<CalEvent>,
    val errors: List<String>,
) {
    /** Evento con hora que está ocurriendo ahora mismo. */
    fun current(now: Instant = Instant.now()): CalEvent? =
        events.filter { !it.allDay && it.start <= now && it.end > now }.minByOrNull { it.end }

    /** Siguiente evento con hora que aún no ha empezado. */
    fun next(now: Instant = Instant.now()): CalEvent? =
        events.filter { !it.allDay && it.start > now }.minByOrNull { it.start }

    fun on(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<CalEvent> {
        val from = day.atStartOfDay(zone).toInstant()
        val to = day.plusDays(1).atStartOfDay(zone).toInstant()
        return events.filter { overlaps(it, from, to) }
            .sortedWith(compareBy<CalEvent>({ !it.allDay }, { it.start }, { it.title }))
    }
}

private fun overlaps(e: CalEvent, from: Instant, to: Instant): Boolean =
    if (e.end > e.start) e.start < to && e.end > from else e.start >= from && e.start < to

object CalendarRepository {
    private const val TTL_MS = 10 * 60 * 1000L
    private const val DAYS_AHEAD = 7L

    private val mutex = Mutex()
    private var cached: CalendarResult? = null
    private var cachedAt = 0L
    private var cachedVersion = -1
    private var cachedDay: LocalDate? = null

    /** Últimos eventos descargados bien de cada calendario (por URL), para no perderlos si falla la red. */
    private val lastGood = HashMap<String, List<CalEvent>>()

    fun peek(): CalendarResult? = cached

    /** Solo para pruebas y capturas: fija unos datos como si se acabaran de descargar. */
    internal fun seed(r: CalendarResult) {
        cached = r
        cachedAt = System.currentTimeMillis()
        cachedVersion = ConfigStore.version
        cachedDay = LocalDate.now()
    }

    /** Momento (ms) de la última descarga; 0 si nunca. */
    val lastUpdated: Long get() = cachedAt

    /** Eventos del mes en curso y de la próxima semana, de todos los calendarios configurados. */
    suspend fun get(config: AppConfig, force: Boolean = false): CalendarResult = mutex.withLock {
        val today = LocalDate.now()
        val fresh = cached != null && cachedVersion == ConfigStore.version && cachedDay == today &&
            System.currentTimeMillis() - cachedAt < TTL_MS
        if (fresh && !force) return@withLock cached!!

        val zone = ZoneId.systemDefault()
        // Desde el día 1 del mes (para el calendario mensual) hasta una semana vista o fin de mes.
        val monthStart = today.withDayOfMonth(1)
        val end = maxOf(today.plusDays(DAYS_AHEAD), monthStart.plusMonths(1))
        val from = monthStart.atStartOfDay(zone).toInstant()
        val to = end.atStartOfDay(zone).toInstant()
        val results = coroutineScope {
            config.calendars.map { src ->
                async(Dispatchers.IO) {
                    runCatching { fetchEvents(src, from, to) }.fold(
                        { it to null },
                        { (lastGood[src.url] ?: emptyList()) to "${src.name.ifEmpty { "Calendario" }}: ${it.message ?: it.javaClass.simpleName}" },
                    )
                }
            }.awaitAll()
        }
        config.calendars.zip(results).forEach { (src, r) -> if (r.second == null) lastGood[src.url] = r.first }
        val result = CalendarResult(results.flatMap { it.first }, results.mapNotNull { it.second })
        cached = result
        cachedAt = System.currentTimeMillis()
        cachedVersion = ConfigStore.version
        cachedDay = today
        result
    }

    /** Descarga un calendario y devuelve cuántos eventos tiene (para el botón "Probar" de la web). */
    suspend fun test(url: String): Int = withContext(Dispatchers.IO) {
        Biweekly.parse(Http.getString(url)).all().sumOf { it.events.size }
    }

    private fun fetchEvents(src: CalendarSource, from: Instant, to: Instant): List<CalEvent> {
        return parseEvents(Http.getString(src.url, mapOf("Accept" to "text/calendar, */*")), src, from, to)
    }

    internal fun parseEvents(text: String, src: CalendarSource, from: Instant, to: Instant): List<CalEvent> {
        val calendars = Biweekly.parse(text).all()
        if (calendars.isEmpty()) error("no es un calendario iCal válido")
        return calendars.flatMap { expand(it, src, from, to) }
    }

    private fun expand(ical: ICalendar, src: CalendarSource, from: Instant, to: Instant): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val events = ical.events

        // Instancias modificadas de eventos recurrentes (RECURRENCE-ID): sustituyen a la original.
        val overridden = events.mapNotNullTo(HashSet()) { ev ->
            val rid = ev.recurrenceId?.value ?: return@mapNotNullTo null
            (ev.uid?.value ?: "") to rid.time
        }

        for (ev in events) {
            if (ev.status?.isCancelled == true) continue
            val dtStart = ev.dateStart ?: continue
            val startDate = dtStart.value ?: continue
            val allDay = !startDate.hasTime()
            val tz: TimeZone = when {
                allDay -> TimeZone.getDefault()
                else -> ical.timezoneInfo.getTimezone(dtStart)?.timeZone
                    ?: if (ical.timezoneInfo.isFloating(dtStart)) TimeZone.getDefault() else TimeZone.getTimeZone("UTC")
            }
            val durationMs = durationOf(ev, startDate, allDay)
            val title = ev.summary?.value?.trim().orEmpty().ifEmpty { "(Sin título)" }
            val location = ev.location?.value?.trim()?.takeIf { it.isNotEmpty() }
            val uid = ev.uid?.value ?: ""

            fun add(start: Date) {
                val s = start.toInstant()
                val e = CalEvent(title, s, s.plusMillis(durationMs), allDay, location, src.name, src.color)
                if (overlaps(e, from, to)) out += e
            }

            val recurring = ev.recurrenceId == null &&
                (ev.recurrenceRule != null || ev.recurrenceDates.isNotEmpty())
            if (!recurring) {
                add(startDate)
                continue
            }
            val it = ev.getDateIterator(tz)
            it.advanceTo(Date(from.toEpochMilli() - durationMs - 1))
            var guard = 0
            while (it.hasNext() && guard++ < 2000) {
                val occ = it.next()
                if (!occ.toInstant().isBefore(to)) break
                if ((uid to occ.time) in overridden) continue
                add(occ)
            }
        }
        return out
    }

    private fun durationOf(ev: VEvent, start: Date, allDay: Boolean): Long {
        ev.dateEnd?.value?.let { end -> return (end.time - start.time).coerceAtLeast(0) }
        ev.duration?.value?.let { return it.toMillis().coerceAtLeast(0) }
        return if (allDay) 24 * 3600 * 1000L else 0L
    }
}
