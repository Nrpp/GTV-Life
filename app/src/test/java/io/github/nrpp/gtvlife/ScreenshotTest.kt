package io.github.nrpp.gtvlife

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalEvent
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.data.CalendarSource
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.data.DayForecast
import io.github.nrpp.gtvlife.data.HourForecast
import io.github.nrpp.gtvlife.data.Weather
import io.github.nrpp.gtvlife.data.WeatherRepository
import io.github.nrpp.gtvlife.ui.CalendarActivity
import io.github.nrpp.gtvlife.ui.ClockActivity
import io.github.nrpp.gtvlife.ui.MainActivity
import io.github.nrpp.gtvlife.ui.SetupActivity
import io.github.nrpp.gtvlife.ui.WeatherActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Genera capturas de cada pantalla con datos de ejemplo (solo con -Pscreenshots=true). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {

    private val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    @Before
    fun seed() {
        assumeTrue(System.getProperty("screenshots") == "true")
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val style = System.getProperty("clockStyle") ?: "digital"
        ConfigStore.save(
            ctx,
            AppConfig(
                locationName = "Madrid, Comunidad de Madrid, España", latitude = 40.4, longitude = -3.7,
                calendars = listOf(CalendarSource("Personal", "https://x/a.ics", "#4FC3F7"), CalendarSource("Trabajo", "https://x/b.ics", "#FF8A65")),
                userName = "Alfonso", clockStyle = style, accent = System.getProperty("accent") ?: "cyan",
            ),
        )
        val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val hourly = (0 until 48).map { i ->
            val t = now.plusHours(i.toLong())
            val temp = 17 + 6 * kotlin.math.sin((t.hour - 9) / 24.0 * 2 * Math.PI)
            val prob = if (t.hour in 17..20) 30 + (t.hour - 16) * 12 else 5
            HourForecast(t, temp, prob, if (prob >= 40) 61 else 2, t.hour in 8..19)
        }
        val today = LocalDate.now()
        WeatherRepository.seed(
            Weather(
                "Madrid, Comunidad de Madrid, España", 19.4, 18.0, 62, 14.0, 2, true, LocalDateTime.now(),
                listOf(
                    DayForecast(today, 61, 23.0, 12.0, 70, 3.2, LocalTime.of(8, 12), LocalTime.of(19, 31), 5.0),
                    DayForecast(today.plusDays(1), 2, 21.0, 11.0, 10, 0.0, LocalTime.of(8, 13), LocalTime.of(19, 29), 4.0),
                    DayForecast(today.plusDays(2), 95, 18.0, 10.0, 80, 9.0, LocalTime.of(8, 14), LocalTime.of(19, 28), 2.0),
                ),
                hourly,
            ),
        )
        val zone = ZoneId.systemDefault()
        fun ev(title: String, day: Long, h: Int, m: Int, mins: Long, cal: Int, loc: String? = null, allDay: Boolean = false): CalEvent {
            val start = if (allDay) LocalDate.now().plusDays(day).atStartOfDay(zone).toInstant()
            else LocalDate.now().plusDays(day).atTime(h, m).atZone(zone).toInstant()
            val src = if (cal == 0) ("Personal" to "#4FC3F7") else ("Trabajo" to "#FF8A65")
            return CalEvent(title, start, start.plus(Duration.ofMinutes(if (allDay) 1440 else mins)), allDay, loc, src.first, src.second)
        }
        val nowH = LocalTime.now().hour
        CalendarRepository.seed(
            CalendarResult(
                listOf(
                    ev("Cumpleaños de Marta", 0, 0, 0, 0, 0, allDay = true),
                    ev("Reunión de equipo", 0, (nowH - 2).coerceAtLeast(0), 0, 60, 1, "Sala 3"),
                    ev("Revisión del proyecto", 0, nowH, 0, 90, 1, "Videollamada"),
                    ev("Gimnasio", 0, (nowH + 2).coerceAtMost(22), 30, 60, 0, "Básico Fit"),
                    ev("Cena con amigos", 0, (nowH + 3).coerceAtMost(23), 0, 120, 0, "La Tagliatella"),
                    ev("Dentista", 1, 10, 0, 45, 0, "Clínica Sonrisas"),
                    ev("Entrega informe", 2, 12, 0, 30, 1),
                    ev("Partido de pádel", 3, 19, 0, 90, 0),
                ),
                emptyList(),
            ),
        )
    }

    private fun shoot(cls: Class<out Activity>, name: String, settleMs: Long = 2800) {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val controller = Robolectric.buildActivity(cls).setup()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(settleMs))
        val view = controller.get().window.decorView
        val bmp = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1920, 1080)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        view.draw(Canvas(bmp))
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun main() = shoot(MainActivity::class.java, "1-menu-${System.getProperty("accent") ?: "cyan"}")
    @Test fun clock() = shoot(ClockActivity::class.java, "2-reloj-${System.getProperty("clockStyle") ?: "digital"}")
    @Test fun calendar() = shoot(CalendarActivity::class.java, "3-calendario")
    @Test fun weather() = shoot(WeatherActivity::class.java, "4-tiempo")
    @Test fun setup() = shoot(SetupActivity::class.java, "5-ajustes")
}
