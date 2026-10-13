package io.github.nrpp.gtvlife.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    val ES: Locale = Locale.forLanguageTag("es-ES")
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ES)
    private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM", ES)
    private val h24 = DateTimeFormatter.ofPattern("HH:mm")
    private val h12 = DateTimeFormatter.ofPattern("h:mm")

    fun longDate(d: LocalDate) = longDate.format(d).replaceFirstChar { it.titlecase(ES) }
    fun shortDate(d: LocalDate) = shortDate.format(d).replaceFirstChar { it.titlecase(ES) }

    fun time(t: LocalTime, cfg: AppConfig) =
        if (cfg.use24h) h24.format(t) else h12.format(t) + if (t.hour < 12) " a. m." else " p. m."

    fun hour(t: LocalTime) = h24.format(t)

    fun temp(v: Double) = "${v.roundToInt()}°"

    fun eventTime(e: CalEvent, day: LocalDate, cfg: AppConfig): String {
        if (e.allDay) return "Todo el día"
        val zone = ZoneId.systemDefault()
        val dayStart = day.atStartOfDay(zone).toInstant()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant()
        fun t(i: Instant) = time(i.atZone(zone).toLocalTime(), cfg)
        val start = if (e.start < dayStart) "…" else t(e.start)
        if (e.end <= e.start) return start
        val end = if (e.end > dayEnd) "…" else t(e.end)
        return "$start – $end"
    }
}

/** Atenuación del brillo en el modo reloj. */
object Dim {
    fun shouldDim(cfg: AppConfig, now: LocalTime = LocalTime.now()): Boolean {
        if (!cfg.dimEnabled) return false
        if (!cfg.dimNightOnly) return true
        val start = LocalTime.parse(cfg.nightStart)
        val end = LocalTime.parse(cfg.nightEnd)
        return if (start <= end) now >= start && now < end else now >= start || now < end
    }

    /** Oscurece con una capa negra y, si el dispositivo lo permite, baja el brillo de la pantalla. */
    fun apply(activity: Activity, overlay: View, dim: Boolean, level: Int) {
        overlay.animate().alpha(if (dim) level / 100f else 0f).setDuration(800).start()
        val lp = activity.window.attributes
        lp.screenBrightness = if (dim) (1f - level / 100f).coerceIn(0.02f, 1f)
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

fun Context.text(value: CharSequence, sp: Float, color: Int = getColor(R.color.text), bold: Boolean = false) =
    TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

fun colorDot(context: Context, hex: String, sizeDp: Int = 14) = View(context).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(runCatching { Color.parseColor(hex) }.getOrDefault(Color.CYAN))
    }
    layoutParams = android.widget.LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
}

/** Pequeño zoom al recibir el foco del mando. */
fun View.focusZoom(scale: Float = 1.04f) {
    setOnFocusChangeListener { v, has ->
        v.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).setDuration(150).start()
    }
}
