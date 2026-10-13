package io.github.nrpp.gtvlife

import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.data.CalendarSource
import io.github.nrpp.gtvlife.ui.Dim
import io.github.nrpp.gtvlife.update.UpdateManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

class LogicTest {

    @Test
    fun versionComparison() {
        assertTrue(UpdateManager.isNewer("1.2.0", "1.1.9"))
        assertTrue(UpdateManager.isNewer("2.0.0", "1.10.10"))
        assertFalse(UpdateManager.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateManager.isNewer("1.1.0", "1.2.0"))
        assertFalse(UpdateManager.isNewer("nightly", "1.2.0"))
    }

    @Test
    fun dimNightWindowWrapsMidnight() {
        val cfg = AppConfig(dimEnabled = true, dimNightOnly = true, nightStart = "22:00", nightEnd = "07:00")
        assertTrue(Dim.shouldDim(cfg, LocalTime.of(23, 30)))
        assertTrue(Dim.shouldDim(cfg, LocalTime.of(3, 0)))
        assertFalse(Dim.shouldDim(cfg, LocalTime.of(12, 0)))
        assertFalse(Dim.shouldDim(cfg.copy(dimEnabled = false), LocalTime.of(23, 30)))
        assertTrue(Dim.shouldDim(cfg.copy(dimNightOnly = false), LocalTime.of(12, 0)))
    }

    @Test
    fun configSanitizes() {
        val cfg = AppConfig.fromJson(
            JSONObject("""{"latitude":40.4,"longitude":-3.7,"dimLevel":500,"nightStart":"bad",
                "calendars":[{"name":"A","url":"javascript:alert(1)"},{"name":"B","url":"webcal://x/y.ics","color":"red"}]}"""),
        )
        assertEquals(95, cfg.dimLevel)
        assertEquals("22:00", cfg.nightStart)
        assertEquals(1, cfg.calendars.size)
        assertEquals("#4FC3F7", cfg.calendars[0].color)
        assertTrue(cfg.hasLocation)
    }

    @Test
    fun newSettingsAreValidated() {
        val cfg = AppConfig.fromJson(JSONObject("""{"accent":"rosa","clockStyle":"analog","autoClockMinutes":999,"userName":"  Alfonso  "}"""))
        assertEquals("cyan", cfg.accent)
        assertEquals("analog", cfg.clockStyle)
        assertEquals(120, cfg.autoClockMinutes)
        assertEquals("Alfonso", cfg.userName)
        val back = AppConfig.fromJson(cfg.toJson())
        assertEquals(cfg, back)
    }

    @Test
    fun moduleSettingsAreValidated() {
        val cfg = AppConfig.fromJson(
            JSONObject(
                """{"modules":["moon","nope","news","moon"],"worldClocks":["Asia/Tokyo","Mars/Base"],
                "cryptoCoins":["Bitcoin","bad coin!"],"currencies":["usd","EUR","XX"],
                "newsFeeds":["ftp://x","https://a.com/rss"],"electricityZone":"ZZZ",
                "countdowns":[{"name":"Viaje","date":"2026-12-20"},{"name":"","date":"2026-01-01"},{"name":"Mal","date":"ayer"}],
                "notes":[{"text":"Leche","done":true},{"text":"  "}]}""",
            ),
        )
        assertEquals(listOf("moon", "news"), cfg.modules)
        assertEquals(listOf("Asia/Tokyo"), cfg.worldClocks)
        assertEquals(listOf("bitcoin"), cfg.cryptoCoins)
        assertEquals(listOf("USD"), cfg.currencies)
        assertEquals(listOf("https://a.com/rss"), cfg.newsFeeds)
        assertEquals("PCB", cfg.electricityZone)
        assertEquals(1, cfg.countdowns.size)
        assertEquals(listOf(io.github.nrpp.gtvlife.data.Note("Leche", true)), cfg.notes)
        assertEquals(cfg, AppConfig.fromJson(cfg.toJson()))
        // Sin la clave "modules" (configuraciones antiguas) se activan los módulos por defecto
        assertEquals(io.github.nrpp.gtvlife.data.ModuleId.DEFAULT_ENABLED, AppConfig.fromJson(JSONObject("{}")).modules)
    }

    @Test
    fun moonPhases() {
        // Luna llena del 26 de octubre de 2026 y luna nueva del 10 de octubre de 2026
        val full = io.github.nrpp.gtvlife.data.Moon.at(java.time.Instant.parse("2026-10-26T04:00:00Z"), java.time.ZoneOffset.UTC)
        assertEquals("Luna llena", full.name)
        assertTrue(full.illumination > 0.98)
        val newMoon = io.github.nrpp.gtvlife.data.Moon.at(java.time.Instant.parse("2026-10-10T16:00:00Z"), java.time.ZoneOffset.UTC)
        assertEquals("Luna nueva", newMoon.name)
        assertTrue(newMoon.illumination < 0.02)
    }

    @Test
    fun yearlyCountdownRollsOver() {
        val today = LocalDate.of(2026, 10, 13)
        val bday = io.github.nrpp.gtvlife.data.Countdown("Cumple", LocalDate.of(1990, 3, 1), yearly = true)
        assertEquals(LocalDate.of(2027, 3, 1), bday.next(today))
        val soon = io.github.nrpp.gtvlife.data.Countdown("Navidad", LocalDate.of(2000, 12, 25), yearly = true)
        assertEquals(LocalDate.of(2026, 12, 25), soon.next(today))
    }

    @Test
    fun timerFormatAndAdjust() {
        val t = io.github.nrpp.gtvlife.data.TimerState
        assertEquals("05:00", t.format(5 * 60_000))
        assertEquals("1:02:03", t.format((3600 + 120 + 3) * 1000L))
        t.reset()
        val before = t.displayMs()
        t.add(60_000)
        assertEquals(before + 60_000, t.displayMs())
        t.add(-60_000)
        assertEquals(before, t.displayMs())
    }

    @Test
    fun relativeTimes() {
        val now = java.time.Instant.parse("2026-10-13T10:00:00Z")
        assertEquals("en 25 min", io.github.nrpp.gtvlife.ui.Fmt.until(now.plusSeconds(25 * 60), now))
        assertEquals("en 1 h 20 min", io.github.nrpp.gtvlife.ui.Fmt.until(now.plusSeconds(80 * 60), now))
        assertEquals("en 2 h", io.github.nrpp.gtvlife.ui.Fmt.until(now.plusSeconds(120 * 60), now))
        assertEquals("quedan 45 min", io.github.nrpp.gtvlife.ui.Fmt.remaining(now.plusSeconds(45 * 60), now))
        assertEquals("queda 1 min", io.github.nrpp.gtvlife.ui.Fmt.remaining(now.plusSeconds(90), now))
        assertEquals("termina ya", io.github.nrpp.gtvlife.ui.Fmt.remaining(now.plusSeconds(20), now))
        assertEquals("Buenos días, Ana", io.github.nrpp.gtvlife.ui.Fmt.greeting(LocalTime.of(9, 0), "Ana"))
        assertEquals("Buenas noches", io.github.nrpp.gtvlife.ui.Fmt.greeting(LocalTime.of(23, 0), ""))
    }

    @Test
    fun expandsRecurringEventsAndOverrides() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid"))
        val zone = ZoneId.of("Europe/Madrid")
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:test
            BEGIN:VEVENT
            UID:daily@test
            DTSTART;TZID=Europe/Madrid:20261001T090000
            DTEND;TZID=Europe/Madrid:20261001T093000
            RRULE:FREQ=DAILY
            EXDATE;TZID=Europe/Madrid:20261014T090000
            SUMMARY:Daily
            END:VEVENT
            BEGIN:VEVENT
            UID:daily@test
            RECURRENCE-ID;TZID=Europe/Madrid:20261015T090000
            DTSTART;TZID=Europe/Madrid:20261015T110000
            DTEND;TZID=Europe/Madrid:20261015T113000
            SUMMARY:Daily (moved)
            END:VEVENT
            BEGIN:VEVENT
            UID:allday@test
            DTSTART;VALUE=DATE:20261013
            DTEND;VALUE=DATE:20261014
            SUMMARY:Holiday
            END:VEVENT
            BEGIN:VEVENT
            UID:utc@test
            DTSTART:20261013T180000Z
            DTEND:20261013T190000Z
            SUMMARY:UTC meeting
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")
        val day = LocalDate.of(2026, 10, 13)
        val from = day.atStartOfDay(zone).toInstant()
        val to = day.plusDays(7).atStartOfDay(zone).toInstant()
        val events = CalendarRepository.parseEvents(ics, CalendarSource("T", "https://x", "#ffffff"), from, to)
        val r = CalendarResult(events, emptyList())

        val d13 = r.on(day, zone)
        assertEquals(listOf("Holiday", "Daily", "UTC meeting"), d13.map { it.title })
        assertTrue(d13[0].allDay)
        assertEquals(LocalTime.of(9, 0), d13[1].start.atZone(zone).toLocalTime())
        assertEquals(LocalTime.of(20, 0), d13[2].start.atZone(zone).toLocalTime())

        assertTrue(r.on(day.plusDays(1), zone).isEmpty()) // EXDATE
        val d15 = r.on(day.plusDays(2), zone)
        assertEquals(listOf("Daily (moved)"), d15.map { it.title })
        assertEquals(LocalTime.of(11, 0), d15[0].start.atZone(zone).toLocalTime())
        assertEquals(1, r.on(day.plusDays(3), zone).size)
    }
}
