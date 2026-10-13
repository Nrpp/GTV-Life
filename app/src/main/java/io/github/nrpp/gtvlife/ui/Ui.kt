package io.github.nrpp.gtvlife.ui

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalEvent
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.ChipDrawable
import java.time.Duration
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
    fun shortDate(d: LocalDate) = shortDate.format(d).replace(".", "").replaceFirstChar { it.titlecase(ES) }

    fun time(t: LocalTime, cfg: AppConfig) =
        if (cfg.use24h) h24.format(t) else h12.format(t) + if (t.hour < 12) " a. m." else " p. m."

    /** Solo dígitos (sin a. m./p. m.), para las tipografías grandes. */
    fun digits(t: LocalTime, cfg: AppConfig): String = if (cfg.use24h) h24.format(t) else h12.format(t)

    fun hour(t: LocalTime) = h24.format(t)

    fun temp(v: Double) = "${v.roundToInt()}°"

    fun greeting(now: LocalTime, name: String): String {
        val base = when (now.hour) {
            in 6..13 -> "Buenos días"
            in 14..20 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        return if (name.isBlank()) base else "$base, ${name.trim()}"
    }

    /** "en 25 min", "en 1 h 20 min", "en 3 días". */
    fun until(target: Instant, now: Instant = Instant.now()): String {
        val mins = Duration.between(now, target).toMinutes().coerceAtLeast(0)
        return when {
            mins < 1 -> "ahora"
            mins < 60 -> "en $mins min"
            mins < 24 * 60 -> {
                val h = mins / 60
                val m = mins % 60
                if (m == 0L) "en $h h" else "en $h h $m min"
            }
            else -> "en ${mins / (24 * 60)} días"
        }
    }

    /** "quedan 25 min" para un evento en curso. */
    fun remaining(end: Instant, now: Instant = Instant.now()): String {
        val mins = Duration.between(now, end).toMinutes()
        if (mins < 1) return "termina ya"
        return (if (mins >= 2) "quedan " else "queda ") + until(end, now).removePrefix("en ")
    }

    fun ago(epochMs: Long): String {
        if (epochMs <= 0) return "—"
        val mins = (System.currentTimeMillis() - epochMs) / 60_000
        return when {
            mins < 1 -> "ahora mismo"
            mins < 60 -> "hace $mins min"
            else -> "hace ${mins / 60} h"
        }
    }

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
        overlay.animate().alpha(if (dim) level / 100f else 0f).setDuration(1200).start()
        val lp = activity.window.attributes
        lp.screenBrightness = if (dim) (1f - level / 100f).coerceIn(0.02f, 1f)
        else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Float) = v * resources.displayMetrics.density

fun Context.text(
    value: CharSequence,
    sp: Float,
    color: Int = Palette.TEXT,
    font: Typeface = Palette.medium(this),
    spacing: Float = 0f,
) = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
    typeface = font
    letterSpacing = spacing
    includeFontPadding = false
}

/** Etiqueta de sección estilo HUD: "// TEXTO". */
fun Context.label(value: String, color: Int = Palette.primary) =
    text("// " + value.uppercase(Fmt.ES), 13f, color, Palette.display(this), 0.22f)

/** Chip de información (pastilla con borde). */
fun Context.chip(value: CharSequence, color: Int = Palette.primary, sp: Float = 16f) =
    text(value, sp, Palette.TEXT, Palette.semibold(this), 0.04f).apply {
        background = ChipDrawable(resources.displayMetrics.density, color)
        setPadding(dp(14), dp(7), dp(14), dp(7))
        gravity = Gravity.CENTER_VERTICAL
    }

fun Context.row(gravity: Int = Gravity.CENTER_VERTICAL) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    this.gravity = gravity
}

fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun colorDot(context: Context, hex: String, sizeDp: Int = 14) = View(context).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(runCatching { android.graphics.Color.parseColor(hex) }.getOrDefault(Palette.primary))
    }
    layoutParams = LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
}

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

fun LinearLayout.LayoutParams.margins(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) = apply { setMargins(l, t, r, b) }

/** Entrada escalonada: aparece desde abajo con un fundido. */
fun View.enter(delayMs: Long, distancePx: Float = 40f) {
    alpha = 0f
    translationY = distancePx
    animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(520)
        .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
}
