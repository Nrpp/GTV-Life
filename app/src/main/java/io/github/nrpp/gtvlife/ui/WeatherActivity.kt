package io.github.nrpp.gtvlife.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.data.DayForecast
import io.github.nrpp.gtvlife.data.Weather
import io.github.nrpp.gtvlife.data.WeatherCodes
import io.github.nrpp.gtvlife.data.WeatherRepository
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.ChipDrawable
import io.github.nrpp.gtvlife.ui.widget.HudPanelDrawable
import io.github.nrpp.gtvlife.ui.widget.SunArcView
import io.github.nrpp.gtvlife.ui.widget.TempChartView
import io.github.nrpp.gtvlife.ui.widget.WeatherIconView
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

/** El tiempo de hoy en detalle: ahora, lluvia, gráfica de 24 h, sol y próximos días. */
class WeatherActivity : BaseActivity() {

    private lateinit var content: LinearLayout
    private var hero: View? = null

    override fun preferredFocus(): View? = hero

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = column().apply { setPadding(dp(52), dp(30), dp(52), dp(30)) }
        val scroll = ScrollView(this).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        setScreen(scroll)

        content.addView(label("El tiempo"))
        content.addView(text(config.locationName.ifBlank { "Ubicación no configurada" }, 34f, Palette.TEXT, Palette.semibold(this)).apply { setPadding(0, dp(8), 0, 0) })

        if (!config.hasLocation) {
            content.addView(text("Configura tu ubicación desde Ajustes escaneando el QR.", 22f, Palette.TEXT_DIM).apply { setPadding(0, dp(20), 0, 0) })
            return
        }
        WeatherRepository.peek()?.let(::render) ?: content.addView(text("Cargando…", 22f, Palette.TEXT_DIM))
        lifecycleScope.launch {
            runCatching { WeatherRepository.get(config) }
                .onSuccess(::render)
                .onFailure {
                    if (WeatherRepository.peek() == null) {
                        content.addView(text("No se pudo obtener el tiempo: ${it.message}", 20f, Palette.WARN))
                    }
                }
        }
    }

    private fun panel(strong: Boolean = false) = column().apply {
        background = HudPanelDrawable(resources.displayMetrics.density, 16f, strong)
        setPadding(dp(24), dp(18), dp(24), dp(18))
        hudFocus(16f, 1.015f)
    }

    private fun stat(name: String, value: String) = column().apply {
        background = ChipDrawable(resources.displayMetrics.density, Palette.primary, 0.06f)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        addView(text(name, 10f, Palette.TEXT_DIM, Palette.display(this@WeatherActivity), 0.18f))
        addView(text(value, 22f, Palette.TEXT, Palette.semibold(this@WeatherActivity)).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun render(w: Weather) {
        while (content.childCount > 2) content.removeViewAt(2)
        val today = w.today

        // Ahora
        val hero = panel(strong = true).also { this.hero = it }
        val heroRow = row()
        heroRow.addView(WeatherIconView(this).apply { set(w.code, w.isDay) }, lp(dp(150), dp(150)))
        heroRow.addView(text(Fmt.temp(w.temp), 96f, Palette.TEXT, Palette.display(this)).apply {
            setShadowLayer(dpf(20f), 0f, 0f, Palette.alpha(Palette.primary, 0.4f))
        }, lp(WRAP, WRAP).margins(l = dp(18), r = dp(28)))
        val desc = column()
        desc.addView(text(WeatherCodes.describe(w.code), 32f, Palette.TEXT, Palette.bold(this)))
        desc.addView(text("Sensación de ${Fmt.temp(w.apparent)}", 20f, Palette.TEXT_DIM).apply { setPadding(0, dp(4), 0, 0) })
        today?.let { desc.addView(text("↑ ${Fmt.temp(it.max)}    ↓ ${Fmt.temp(it.min)}", 22f, Palette.TEXT, Palette.semibold(this)).apply { setPadding(0, dp(8), 0, 0) }) }
        heroRow.addView(desc, lp(0, WRAP, 1f))
        val stats = column()
        val s1 = row()
        s1.addView(stat("HUMEDAD", "${w.humidity}%"), lp(dp(130), WRAP).margins(r = dp(10)))
        s1.addView(stat("VIENTO", "${w.wind.roundToInt()} km/h"), lp(dp(130), WRAP))
        val s2 = row()
        s2.addView(stat("ÍNDICE UV", today?.let { uvText(it.uvMax) } ?: "—"), lp(dp(130), WRAP).margins(r = dp(10)))
        s2.addView(stat("LLUVIA", "${today?.precipProb ?: 0}%"), lp(dp(130), WRAP))
        stats.addView(s1)
        stats.addView(s2, lp(WRAP, WRAP).margins(t = dp(10)))
        heroRow.addView(stats)
        hero.addView(heroRow)
        content.addView(hero, lp(MATCH, WRAP).margins(t = dp(20)))

        // ¿Va a llover?
        val rain = w.willRainToday
        val banner = row().apply {
            background = ChipDrawable(resources.displayMetrics.density, if (rain) Palette.WARN else Palette.OK, 0.12f)
            setPadding(dp(22), dp(14), dp(22), dp(14))
            hudFocus(10f, 1.015f)
        }
        banner.addView(text(if (rain) "☂" else "✓", 26f, if (rain) Palette.WARN else Palette.OK, Palette.bold(this)))
        banner.addView(text(w.rainSummary(), 22f, Palette.TEXT, Palette.semibold(this)), lp(0, WRAP, 1f).margins(l = dp(16)))
        today?.takeIf { rain && it.precipSum > 0.05 }?.let {
            banner.addView(text("${"%.1f".format(Fmt.ES, it.precipSum)} mm", 18f, Palette.TEXT_DIM, Palette.display(this)))
        }
        content.addView(banner, lp(MATCH, WRAP).margins(t = dp(18)))

        // Gráfica de 24 h
        val chartPanel = panel()
        chartPanel.addView(label("Próximas 24 horas"))
        chartPanel.addView(TempChartView(this).apply { hours = w.next(24) }, lp(MATCH, dp(200)).margins(t = dp(10)))
        content.addView(chartPanel, lp(MATCH, WRAP).margins(t = dp(18)))

        // Sol y próximos días
        val bottom = row(Gravity.TOP)
        val sun = panel()
        sun.addView(label("Sol"))
        sun.addView(SunArcView(this).apply { set(today?.sunrise, today?.sunset, LocalTime.now()) }, lp(MATCH, dp(130)).margins(t = dp(10)))
        bottom.addView(sun, lp(0, MATCH, 1.2f).margins(r = dp(18)))
        w.days.drop(1).take(2).forEachIndexed { i, d ->
            bottom.addView(dayPanel(if (i == 0) "Mañana" else Fmt.shortDate(d.date), d), lp(0, MATCH, 1f).margins(r = if (i == 0) dp(18) else 0))
        }
        content.addView(bottom, lp(MATCH, WRAP).margins(t = dp(18)))

        content.addView(text("ACTUALIZADO ${Fmt.ago(WeatherRepository.lastUpdated).uppercase(Fmt.ES)}  ·  DATOS: OPEN-METEO", 11f, Palette.TEXT_FAINT, Palette.display(this), 0.18f).apply {
            setPadding(0, dp(18), 0, 0)
        })
        hero.requestFocus()
    }

    private fun dayPanel(name: String, d: DayForecast): View = panel().apply {
        addView(label(name))
        val r = row()
        r.addView(WeatherIconView(this@WeatherActivity).apply { set(d.code, true); animated = false }, lp(dp(70), dp(70)))
        val c = column()
        c.addView(text(WeatherCodes.describe(d.code), 19f, Palette.TEXT, Palette.semibold(this@WeatherActivity)))
        c.addView(text("↑ ${Fmt.temp(d.max)}   ↓ ${Fmt.temp(d.min)}", 20f, Palette.TEXT, Palette.display(this@WeatherActivity)).apply { setPadding(0, dp(6), 0, 0) })
        c.addView(text("Lluvia ${d.precipProb}%", 16f, if (d.precipProb >= 50) Palette.WARN else Palette.TEXT_DIM).apply { setPadding(0, dp(6), 0, 0) })
        r.addView(c, lp(0, WRAP, 1f).margins(l = dp(14)))
        addView(r, lp(MATCH, WRAP).margins(t = dp(14)))
    }

    private fun uvText(uv: Double): String {
        val v = uv.roundToInt()
        val level = when {
            v <= 2 -> "bajo"
            v <= 5 -> "medio"
            v <= 7 -> "alto"
            else -> "muy alto"
        }
        return "$v $level"
    }
}
