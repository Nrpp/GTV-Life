package io.github.nrpp.gtvlife.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import io.github.nrpp.gtvlife.data.HourForecast
import io.github.nrpp.gtvlife.ui.theme.Palette
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun View.dpf(v: Float) = v * resources.displayMetrics.density
private fun View.spf(v: Float) = v * resources.displayMetrics.scaledDensity

/** Curva de temperatura de las próximas horas con barras de probabilidad de lluvia. */
class TempChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var hours: List<HourForecast> = emptyList()
        set(value) { field = value; invalidate() }

    /** Versión mini sin etiquetas, para la tarjeta del menú. */
    var compact = false

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val area = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val curve = Path()
    private val fillPath = Path()
    private val hourFmt = DateTimeFormatter.ofPattern("HH")

    override fun onDraw(canvas: Canvas) {
        val data = hours
        if (data.size < 2) return
        val padX = dpf(if (compact) 4f else 18f)
        val top = dpf(if (compact) 6f else 30f)
        val bottom = height - dpf(if (compact) 4f else 26f)
        val w = width - padX * 2
        val minT = data.minOf { it.temp } - 1.0
        val maxT = data.maxOf { it.temp } + 1.0
        val curveBottom = top + (bottom - top) * 0.72f
        fun x(i: Int) = padX + w * i / (data.size - 1)
        fun y(t: Double) = (curveBottom - (t - minT) / (maxT - minT) * (curveBottom - top)).toFloat()

        if (!compact) {
            grid.color = Color.argb(18, 255, 255, 255)
            grid.strokeWidth = dpf(1f)
            for (k in 0..3) {
                val gy = top + (bottom - top) * k / 3f
                canvas.drawLine(padX, gy, padX + w, gy, grid)
            }
        }

        // Barras de lluvia
        val barW = w / data.size * 0.42f
        data.forEachIndexed { i, h ->
            if (h.precipProb <= 0) return@forEachIndexed
            val bh = (bottom - top) * 0.32f * h.precipProb / 100f
            bar.color = Palette.alpha(if (h.precipProb >= 40) Palette.WARN else 0xFF4FA8FF.toInt(), if (compact) 0.45f else 0.55f)
            canvas.drawRect(x(i) - barW / 2, bottom - bh, x(i) + barW / 2, bottom, bar)
        }

        // Curva suave
        curve.reset()
        curve.moveTo(x(0), y(data[0].temp))
        for (i in 1 until data.size) {
            val px = x(i - 1); val py = y(data[i - 1].temp)
            val cx = x(i); val cy = y(data[i].temp)
            val mx = (px + cx) / 2f
            curve.cubicTo(mx, py, mx, cy, cx, cy)
        }
        fillPath.set(curve)
        fillPath.lineTo(x(data.size - 1), bottom)
        fillPath.lineTo(x(0), bottom)
        fillPath.close()
        area.shader = LinearGradient(0f, top, 0f, bottom, Palette.alpha(Palette.primary, 0.32f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(fillPath, area)

        line.shader = LinearGradient(padX, 0f, padX + w, 0f, Palette.primary, Palette.secondary, Shader.TileMode.CLAMP)
        line.strokeWidth = dpf(7f); line.alpha = 40
        canvas.drawPath(curve, line)
        line.strokeWidth = dpf(2.5f); line.alpha = 255
        canvas.drawPath(curve, line)

        // Punto "ahora"
        bar.color = Palette.alpha(Palette.primary, 0.3f)
        canvas.drawCircle(x(0), y(data[0].temp), dpf(9f), bar)
        bar.color = Color.WHITE
        canvas.drawCircle(x(0), y(data[0].temp), dpf(4f), bar)

        if (compact) return
        text.typeface = Palette.semibold(context)
        val every = if (data.size > 12) 3 else 1
        data.forEachIndexed { i, h ->
            if (i % every != 0) return@forEachIndexed
            text.textSize = spf(15f)
            text.color = Palette.TEXT
            canvas.drawText("${h.temp.roundToInt()}°", x(i), y(h.temp) - dpf(12f), text)
            text.textSize = spf(13f)
            text.color = Palette.TEXT_DIM
            canvas.drawText(if (i == 0) "AHORA" else hourFmt.format(h.time) + "h", x(i), height - dpf(6f), text)
            if (h.precipProb >= 20) {
                text.color = if (h.precipProb >= 40) Palette.WARN else 0xFF7FC0FF.toInt()
                text.textSize = spf(12f)
                canvas.drawText("${h.precipProb}%", x(i), bottom - dpf(4f) - (bottom - top) * 0.32f * h.precipProb / 100f, text)
            }
        }
    }
}

/** Arco del sol entre el amanecer y el atardecer, con la posición actual. */
class SunArcView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var sunrise: LocalTime? = null
    var sunset: LocalTime? = null
    var now: LocalTime = LocalTime.now()

    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    fun set(sunrise: LocalTime?, sunset: LocalTime?, now: LocalTime) {
        this.sunrise = sunrise; this.sunset = sunset; this.now = now
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val rise = sunrise ?: return
        val set = sunset ?: return
        val gold = 0xFFFFC94A.toInt()
        val pad = dpf(14f)
        val baseY = height - dpf(30f)
        val rect = RectF(pad, dpf(10f), width - pad, baseY + (baseY - dpf(10f)))
        val total = (set.toSecondOfDay() - rise.toSecondOfDay()).toFloat()
        val f = ((now.toSecondOfDay() - rise.toSecondOfDay()) / total).coerceIn(0f, 1f)
        val daytime = now in rise..set

        arc.strokeWidth = dpf(2f)
        arc.pathEffect = DashPathEffect(floatArrayOf(dpf(4f), dpf(6f)), 0f)
        arc.color = Color.argb(70, 255, 255, 255)
        canvas.drawArc(rect, 180f, 180f, false, arc)
        arc.pathEffect = null
        arc.strokeWidth = dpf(3f)
        arc.color = gold
        if (daytime) canvas.drawArc(rect, 180f, 180f * f, false, arc)

        arc.strokeWidth = dpf(1f)
        arc.color = Color.argb(60, 255, 255, 255)
        canvas.drawLine(0f, baseY, width.toFloat(), baseY, arc)

        val ang = PI + PI * f
        val cx = rect.centerX() + (rect.width() / 2f) * cos(ang).toFloat()
        val cy = rect.centerY() + (rect.height() / 2f) * sin(ang).toFloat()
        dot.color = Palette.alpha(if (daytime) gold else Palette.TEXT_DIM, 0.25f)
        canvas.drawCircle(cx, cy, dpf(13f), dot)
        dot.color = if (daytime) gold else Palette.TEXT_DIM
        canvas.drawCircle(cx, cy, dpf(6f), dot)

        text.typeface = Palette.semibold(context)
        text.textSize = spf(15f)
        text.color = Palette.TEXT_DIM
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("↑ " + fmt.format(rise), 0f, height - dpf(6f), text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(fmt.format(set) + " ↓", width.toFloat(), height - dpf(6f), text)
    }
}

/** 60 segmentos que se van encendiendo con los segundos. */
class SecondsBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var second: Int = 0
        set(value) { if (field != value) { field = value; invalidate() } }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val n = 60
        val gap = dpf(2f)
        val segW = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val x = i * (segW + gap)
            val major = i % 5 == 0
            val top = if (major) 0f else height * 0.3f
            p.color = when {
                i == second -> Color.WHITE
                i < second -> Palette.alpha(Palette.primary, 0.35f + 0.6f * i / n)
                else -> Color.argb(if (major) 50 else 26, 255, 255, 255)
            }
            canvas.drawRect(x, top, x + segW, height.toFloat(), p)
        }
    }
}

/** Reloj analógico futurista con segundero continuo y arco de progreso. */
class AnalogClockView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = s / 2f - dpf(8f)
        val now = LocalTime.now()
        val sec = now.second + now.nano / 1e9f
        val min = now.minute + sec / 60f
        val hour = (now.hour % 12) + min / 60f

        // Anillos
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1f)
        p.color = Color.argb(40, 255, 255, 255)
        canvas.drawCircle(cx, cy, r, p)
        canvas.drawCircle(cx, cy, r * 0.62f, p)
        p.color = Palette.alpha(Palette.primary, 0.15f)
        p.strokeWidth = dpf(10f)
        canvas.drawCircle(cx, cy, r * 0.93f, p)
        p.color = Palette.primary
        p.strokeWidth = dpf(3f)
        canvas.drawArc(RectF(cx - r * 0.93f, cy - r * 0.93f, cx + r * 0.93f, cy + r * 0.93f), -90f, 360f * sec / 60f, false, p)

        // Marcas
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90).toFloat()
            val major = i % 5 == 0
            val r1 = r * (if (major) 0.8f else 0.85f)
            val r2 = r * 0.88f
            p.strokeWidth = dpf(if (major) 3f else 1.2f)
            p.color = if (major) Palette.TEXT else Color.argb(90, 255, 255, 255)
            canvas.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, p)
        }
        text.typeface = Palette.display(context)
        text.textSize = s * 0.06f
        text.color = Palette.TEXT_DIM
        listOf(12 to -90.0, 3 to 0.0, 6 to 90.0, 9 to 180.0).forEach { (n, deg) ->
            val a = Math.toRadians(deg).toFloat()
            canvas.drawText("%02d".format(n), cx + cos(a) * r * 0.7f, cy + sin(a) * r * 0.7f + text.textSize / 3f, text)
        }

        fun hand(angleDeg: Float, len: Float, width: Float, color: Int, tail: Float = 0f) {
            val a = Math.toRadians(angleDeg - 90.0).toFloat()
            p.strokeWidth = width
            p.color = color
            canvas.drawLine(cx - cos(a) * tail, cy - sin(a) * tail, cx + cos(a) * len, cy + sin(a) * len, p)
        }
        hand(hour * 30f, r * 0.45f, dpf(9f), Palette.alpha(Color.WHITE, 0.15f))
        hand(hour * 30f, r * 0.45f, dpf(5f), Palette.TEXT)
        hand(min * 6f, r * 0.68f, dpf(7f), Palette.alpha(Color.WHITE, 0.12f))
        hand(min * 6f, r * 0.68f, dpf(3f), Palette.TEXT)
        hand(sec * 6f, r * 0.8f, dpf(6f), Palette.alpha(Palette.primary, 0.25f), r * 0.15f)
        hand(sec * 6f, r * 0.8f, dpf(2f), Palette.primary, r * 0.15f)
        p.style = Paint.Style.FILL
        p.color = Palette.primary
        canvas.drawCircle(cx, cy, dpf(7f), p)
        p.color = Palette.BG0
        canvas.drawCircle(cx, cy, dpf(3f), p)

        if (isShown) postInvalidateDelayed(50)
    }
}

/** Código QR dentro de un marco HUD con una línea de escaneo animada. */
class QrPanelView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var bitmap: Bitmap? = null
        set(value) { field = value; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val scan = Paint()

    override fun onDraw(canvas: Canvas) {
        val pad = dpf(18f)
        val inner = RectF(pad, pad, width - pad, height - pad)
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        canvas.drawRoundRect(inner, dpf(10f), dpf(10f), p)
        bitmap?.let {
            val q = RectF(inner).apply { inset(dpf(12f), dpf(12f)) }
            p.isFilterBitmap = false
            canvas.drawBitmap(it, null, q, p)
        }

        // Esquinas
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(4f)
        p.strokeCap = Paint.Cap.SQUARE
        p.color = Palette.primary
        val l = dpf(34f)
        val o = dpf(4f)
        val w = width.toFloat() - o
        val h = height.toFloat() - o
        canvas.drawLine(o, o, o + l, o, p); canvas.drawLine(o, o, o, o + l, p)
        canvas.drawLine(w - l, o, w, o, p); canvas.drawLine(w, o, w, o + l, p)
        canvas.drawLine(o, h, o + l, h, p); canvas.drawLine(o, h - l, o, h, p)
        canvas.drawLine(w - l, h, w, h, p); canvas.drawLine(w, h - l, w, h, p)

        // Línea de escaneo
        val t = (System.currentTimeMillis() % 2600L) / 2600f
        val f = if (t < 0.5f) t * 2f else 2f - t * 2f
        val y = inner.top + inner.height() * f
        scan.shader = LinearGradient(0f, y - dpf(26f), 0f, y, Color.TRANSPARENT, Palette.alpha(Palette.primary, 0.18f), Shader.TileMode.CLAMP)
        canvas.drawRect(inner.left, y - dpf(26f), inner.right, y, scan)
        p.style = Paint.Style.FILL
        p.color = Palette.primary
        canvas.drawRect(inner.left - dpf(6f), y - dpf(1f), inner.right + dpf(6f), y + dpf(1f), p)

        if (isShown) postInvalidateDelayed(33)
    }
}
