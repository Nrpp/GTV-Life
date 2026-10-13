package io.github.nrpp.gtvlife.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.WeatherRepository
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.AnalogClockView
import io.github.nrpp.gtvlife.ui.widget.SecondsBarView
import io.github.nrpp.gtvlife.ui.widget.WeatherIconView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** Reloj grande a pantalla completa (digital, analógico o minimalista) con atenuación del brillo. */
class ClockActivity : BaseActivity() {

    override val useBackground = false

    private lateinit var container: LinearLayout
    private lateinit var time: TextView
    private var ampm: TextView? = null
    private var seconds: SecondsBarView? = null
    private lateinit var date: TextView
    private lateinit var chips: LinearLayout
    private lateinit var overlay: View
    private lateinit var hint: TextView

    /** null = automático según la configuración; true/false = forzado con el botón OK. */
    private var manualDim: Boolean? = null
    private var dimmed: Boolean? = null
    private var lastShiftMinute = -1
    private val dateFmt = DateTimeFormatter.ofPattern("EEEE  d  MMMM", Fmt.ES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // Halo muy tenue del color de acento en la parte baja (casi negro, apto para OLED)
            foreground = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(Palette.alpha(Palette.primary, 0.07f), Color.TRANSPARENT))
        }
        container = when (config.clockStyle) {
            "analog" -> buildAnalog()
            "minimal" -> buildMinimal()
            else -> buildDigital()
        }
        root.addView(container, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        overlay = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
        hint = text("", 13f, Palette.TEXT_DIM, Palette.display(this), 0.2f).apply { alpha = 0f }
        root.addView(hint, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) })
        setScreen(root)

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
        showHint("OK  ·  ATENUAR / RESTAURAR BRILLO")
        container.enter(0, dpf(24f))
    }

    private fun bigTime(sp: Float, font: android.graphics.Typeface, spacing: Float) =
        text("", sp, Palette.TEXT, font, spacing).apply {
            setShadowLayer(dpf(24f), 0f, 0f, Palette.alpha(Palette.primary, 0.45f))
        }

    private fun buildDigital() = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        val r = row(Gravity.BOTTOM)
        time = bigTime(150f, Palette.display(this@ClockActivity), 0.04f)
        r.addView(time)
        ampm = text("", 30f, Palette.primary, Palette.display(this@ClockActivity)).also {
            r.addView(it, lp(WRAP, WRAP).margins(l = dp(14), b = dp(26)))
        }
        addView(r)
        seconds = SecondsBarView(this@ClockActivity).also { addView(it, lp(dp(620), dp(14)).margins(t = dp(18))) }
        date = text("", 22f, Palette.TEXT_DIM, Palette.display(this@ClockActivity), 0.3f)
        addView(date, lp(WRAP, WRAP).margins(t = dp(26)))
        chips = row(Gravity.CENTER)
        addView(chips, lp(WRAP, WRAP).margins(t = dp(26)))
    }

    private fun buildAnalog() = row().apply {
        addView(AnalogClockView(this@ClockActivity), lp(dp(420), dp(420)))
        val col = column()
        time = bigTime(72f, Palette.display(this@ClockActivity), 0.04f)
        col.addView(time)
        ampm = text("", 22f, Palette.primary, Palette.display(this@ClockActivity)).also { col.addView(it) }
        date = text("", 20f, Palette.TEXT_DIM, Palette.display(this@ClockActivity), 0.25f)
        col.addView(date, lp(WRAP, WRAP).margins(t = dp(14)))
        chips = column()
        col.addView(chips, lp(WRAP, WRAP).margins(t = dp(26)))
        addView(col, lp(WRAP, WRAP).margins(l = dp(56)))
    }

    private fun buildMinimal() = column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        time = bigTime(230f, Palette.light(this@ClockActivity), 0f)
        addView(time)
        date = text("", 30f, Palette.TEXT_DIM, Palette.regular(this@ClockActivity), 0.08f)
        addView(date, lp(WRAP, WRAP).margins(t = dp(4)))
        chips = row(Gravity.CENTER)
        addView(chips, lp(WRAP, WRAP).margins(t = dp(24)))
    }

    private fun tick() {
        val now = LocalTime.now()
        time.text = Fmt.digits(now, config)
        ampm?.let {
            it.visibility = if (config.use24h) View.GONE else View.VISIBLE
            it.text = if (now.hour < 12) "AM" else "PM"
        }
        seconds?.let {
            it.visibility = if (config.showSeconds || config.clockStyle == "digital") View.VISIBLE else View.GONE
            it.second = now.second
        }
        val d = dateFmt.format(LocalDate.now())
        date.text = if (config.clockStyle == "minimal") d.replaceFirstChar { it.titlecase(Fmt.ES) } else d.uppercase(Fmt.ES)

        val shouldDim = manualDim ?: Dim.shouldDim(config, now)
        if (shouldDim != dimmed) {
            dimmed = shouldDim
            Dim.apply(this, overlay, shouldDim, config.dimLevel)
        }

        // Desplaza un poco el reloj cada minuto para evitar marcas en pantallas OLED.
        if (now.minute != lastShiftMinute) {
            lastShiftMinute = now.minute
            val range = dpf(22f)
            container.animate()
                .translationX(Random.nextFloat() * 2 * range - range)
                .translationY(Random.nextFloat() * 2 * range - range)
                .setDuration(2500).start()
        }
    }

    private fun infoChip(content: View, color: Int = Palette.primary) = row().apply {
        background = io.github.nrpp.gtvlife.ui.widget.ChipDrawable(resources.displayMetrics.density, color, 0.08f)
        setPadding(dp(14), dp(8), dp(16), dp(8))
        addView(content)
    }

    private fun addChip(v: View) {
        val vertical = chips.orientation == LinearLayout.VERTICAL
        chips.addView(v, lp(WRAP, WRAP).margins(r = if (vertical) 0 else dp(14), b = if (vertical) dp(12) else 0))
    }

    private suspend fun refreshInfo() {
        chips.removeAllViews()
        if (config.hasLocation) {
            runCatching { WeatherRepository.get(config) }.getOrNull()?.let { w ->
                val content = row()
                content.addView(WeatherIconView(this).apply { set(w.code, w.isDay) }, lp(dp(30), dp(30)))
                content.addView(text(Fmt.temp(w.temp), 20f, Palette.TEXT, Palette.display(this)).apply { setPadding(dp(10), 0, 0, 0) })
                addChip(infoChip(content))
                if (w.willRainToday) addChip(infoChip(text("☂  LLUVIA HOY", 13f, Palette.TEXT, Palette.display(this), 0.12f), Palette.WARN))
            }
        }
        if (config.calendars.isNotEmpty()) {
            runCatching { CalendarRepository.get(config) }.getOrNull()?.let { r ->
                val now = Instant.now()
                val ev = r.current(now) ?: r.next(now)?.takeIf { it.start.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
                if (ev != null) {
                    val whenText = if (ev.start <= now) "AHORA" else Fmt.time(ev.start.atZone(ZoneId.systemDefault()).toLocalTime(), config)
                    val content = row()
                    content.addView(text(whenText, 14f, Palette.primary, Palette.display(this), 0.1f))
                    content.addView(text(ev.title, 19f, Palette.TEXT, Palette.semibold(this)).apply {
                        maxLines = 1; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(360)
                        setPadding(dp(12), 0, 0, 0)
                    })
                    addChip(infoChip(content))
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val current = dimmed ?: false
            manualDim = !current
            showHint(if (!current) "BRILLO ATENUADO" else "BRILLO NORMAL")
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
