package io.github.nrpp.gtvlife.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.data.Weather
import io.github.nrpp.gtvlife.data.WeatherCodes
import io.github.nrpp.gtvlife.data.WeatherRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** El tiempo de hoy en detalle: ahora, lluvia, próximas horas y mañana. */
class WeatherActivity : ComponentActivity() {

    private lateinit var config: AppConfig
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        config = ConfigStore.load(this)
        content = findViewById(R.id.content)
        findViewById<TextView>(R.id.title).text = "El tiempo"
        findViewById<TextView>(R.id.subtitle).text = config.locationName.ifBlank { "Ubicación no configurada" }

        if (!config.hasLocation) {
            content.addView(text("Configura tu ubicación desde Configuración escaneando el QR.", 24f))
            return
        }
        WeatherRepository.peek()?.let(::render) ?: content.addView(text("Cargando…", 24f))
        lifecycleScope.launch {
            runCatching { WeatherRepository.get(config, force = true) }
                .onSuccess(::render)
                .onFailure {
                    if (WeatherRepository.peek() == null) {
                        content.removeAllViews()
                        content.addView(text("No se pudo obtener el tiempo: ${it.message}", 22f, getColor(R.color.warn)))
                    }
                }
        }
    }

    private fun render(w: Weather) {
        content.removeAllViews()
        val dim = getColor(R.color.text_dim)

        // Ahora
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text(WeatherCodes.emoji(w.code, w.isDay), 96f))
            addView(text(Fmt.temp(w.temp), 110f).apply {
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setPadding(dp(24), 0, dp(40), 0)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(WeatherCodes.describe(w.code), 32f))
                addView(text("Sensación ${Fmt.temp(w.apparent)}  ·  Humedad ${w.humidity}%  ·  Viento ${w.wind.roundToInt()} km/h", 20f, dim))
                w.today?.let { d ->
                    val sun = listOfNotNull(d.sunrise?.let { "🌅 ${Fmt.hour(it)}" }, d.sunset?.let { "🌇 ${Fmt.hour(it)}" }).joinToString("   ")
                    addView(text("Máx. ${Fmt.temp(d.max)}  ·  Mín. ${Fmt.temp(d.min)}   $sun", 20f, dim))
                }
            })
        })

        // ¿Va a llover?
        content.addView(text((if (w.willRainToday) "☔  " else "🌂  ") + w.rainSummary(), 28f,
            getColor(if (w.willRainToday) R.color.warn else R.color.ok), bold = true).apply {
            setPadding(0, dp(24), 0, 0)
        })
        w.today?.let { d ->
            if (d.precipSum > 0.05) content.addView(text("Precipitación prevista hoy: ${"%.1f".format(Fmt.ES, d.precipSum)} mm", 20f, dim))
        }

        // Próximas horas
        content.addView(text("Próximas horas", 24f, getColor(R.color.accent), bold = true).apply { setPadding(0, dp(28), 0, dp(12)) })
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        w.next(24).forEach { h ->
            strip.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = getDrawable(R.drawable.row_bg)
                isFocusable = true
                setPadding(dp(16), dp(12), dp(16), dp(12))
                layoutParams = LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
                addView(text(Fmt.hour(h.time.toLocalTime()), 18f, dim))
                addView(text(WeatherCodes.emoji(h.code, h.isDay), 32f).apply { setPadding(0, dp(6), 0, dp(6)) })
                addView(text(Fmt.temp(h.temp), 24f))
                addView(text("💧${h.precipProb}%", 16f, getColor(if (h.precipProb >= 40) R.color.warn else R.color.text_dim)))
            })
        }
        content.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
        })

        // Mañana
        w.tomorrow?.let { d ->
            content.addView(text("Mañana", 24f, getColor(R.color.accent), bold = true).apply { setPadding(0, dp(28), 0, dp(8)) })
            content.addView(text(
                "${WeatherCodes.emoji(d.code)}  ${WeatherCodes.describe(d.code)}  ·  Máx. ${Fmt.temp(d.max)}  ·  Mín. ${Fmt.temp(d.min)}  ·  Lluvia ${d.precipProb}%",
                22f,
            ))
        }

        strip.getChildAt(0)?.requestFocus()
    }
}
