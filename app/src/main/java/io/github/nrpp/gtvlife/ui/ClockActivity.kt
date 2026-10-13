package io.github.nrpp.gtvlife.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.data.WeatherCodes
import io.github.nrpp.gtvlife.data.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** Reloj grande a pantalla completa, con atenuación opcional del brillo. */
class ClockActivity : ComponentActivity() {

    private lateinit var config: AppConfig
    private lateinit var container: View
    private lateinit var time: TextView
    private lateinit var seconds: TextView
    private lateinit var date: TextView
    private lateinit var info: TextView
    private lateinit var overlay: View
    private lateinit var hint: TextView

    /** null = automático según la configuración; true/false = forzado con el botón OK. */
    private var manualDim: Boolean? = null
    private var dimmed: Boolean? = null
    private var lastShiftMinute = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock)
        container = findViewById(R.id.clockContainer)
        time = findViewById(R.id.time)
        seconds = findViewById(R.id.seconds)
        date = findViewById(R.id.date)
        info = findViewById(R.id.info)
        overlay = findViewById(R.id.dimOverlay)
        hint = findViewById(R.id.hint)

        config = ConfigStore.load(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        tick()
                        delay(1000L - System.currentTimeMillis() % 1000L)
                    }
                }
                launch {
                    while (true) {
                        refreshInfo()
                        delay(5 * 60 * 1000L)
                    }
                }
            }
        }
        showHint("OK: atenuar / restaurar brillo")
    }

    private fun tick() {
        val now = LocalTime.now()
        time.text = if (config.use24h) DateTimeFormatter.ofPattern("HH:mm").format(now)
        else DateTimeFormatter.ofPattern("h:mm").format(now)
        seconds.visibility = if (config.showSeconds || !config.use24h) View.VISIBLE else View.GONE
        seconds.text = buildString {
            if (config.showSeconds) append("%02d".format(now.second))
            if (!config.use24h) {
                if (isNotEmpty()) append("\n")
                append(if (now.hour < 12) "a. m." else "p. m.")
            }
        }
        date.text = Fmt.longDate(LocalDate.now())

        val shouldDim = manualDim ?: Dim.shouldDim(config, now)
        if (shouldDim != dimmed) {
            dimmed = shouldDim
            Dim.apply(this, overlay, shouldDim, config.dimLevel)
        }

        // Desplaza un poco el reloj cada minuto para evitar marcas en pantallas OLED.
        if (now.minute != lastShiftMinute) {
            lastShiftMinute = now.minute
            val range = dp(24).toFloat()
            container.animate()
                .translationX(Random.nextFloat() * 2 * range - range)
                .translationY(Random.nextFloat() * 2 * range - range)
                .setDuration(2000).start()
        }
    }

    private suspend fun refreshInfo() {
        val parts = mutableListOf<String>()
        if (config.hasLocation) {
            runCatching { WeatherRepository.get(config) }.getOrNull()?.let { w ->
                parts += "${WeatherCodes.emoji(w.code, w.isDay)} ${Fmt.temp(w.temp)}" +
                    (if (w.willRainToday) " · ☔ lluvia hoy" else "")
            }
        }
        if (config.calendars.isNotEmpty()) {
            runCatching { CalendarRepository.get(config) }.getOrNull()?.let { r ->
                val now = Instant.now()
                val next = r.on(LocalDate.now()).firstOrNull { !it.allDay && it.start >= now }
                if (next != null) {
                    val t = Fmt.time(next.start.atZone(ZoneId.systemDefault()).toLocalTime(), config)
                    parts += "Próximo: $t ${next.title}"
                }
            }
        }
        info.text = parts.joinToString("     ")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val current = dimmed ?: false
            manualDim = !current
            showHint(if (!current) "Brillo atenuado" else "Brillo normal")
            tick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showHint(msg: String) {
        hint.text = msg
        hint.animate().cancel()
        hint.alpha = 1f
        hint.animate().alpha(0f).setStartDelay(2500).setDuration(800).start()
    }
}
