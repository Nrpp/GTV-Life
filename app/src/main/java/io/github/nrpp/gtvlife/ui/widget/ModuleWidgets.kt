package io.github.nrpp.gtvlife.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import io.github.nrpp.gtvlife.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.cos

private fun View.dp(v: Float) = v * resources.displayMetrics.density
private fun View.sp(v: Float) = v * resources.displayMetrics.scaledDensity

/** Luna con la parte iluminada según la fase (0 = nueva, 0.5 = llena). */
class MoonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var phase = 0.0
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f - dp(6f)
        val cx = width / 2f
        val cy = height / 2f
        val moonLight = 0xFFF2F0E6.toInt()

        // Halo
        p.color = Palette.alpha(moonLight, 0.10f)
        canvas.drawCircle(cx, cy, r + dp(5f), p)
        // Disco oscuro
        p.color = 0xFF1A2236.toInt()
        canvas.drawCircle(cx, cy, r, p)

        // Parte iluminada: semicírculo + elipse cuyo ancho depende de la fase
        val f = phase
        val k = cos(2 * Math.PI * f).toFloat() // 1 nueva, -1 llena
        val waxing = f < 0.5
        path.reset()
        val steps = 60
        // Borde exterior iluminado (lado derecho si crece, izquierdo si mengua)
        for (i in 0..steps) {
            val t = -Math.PI / 2 + Math.PI * i / steps
            val x = (cos(t) * r).toFloat() * if (waxing) 1 else -1
            val y = (kotlin.math.sin(t) * r).toFloat()
            if (i == 0) path.moveTo(cx + x, cy + y) else path.lineTo(cx + x, cy + y)
        }
        // Terminador (elipse)
        for (i in steps downTo 0) {
            val t = -Math.PI / 2 + Math.PI * i / steps
            val x = (cos(t) * r * k).toFloat() * if (waxing) 1 else -1
            val y = (kotlin.math.sin(t) * r).toFloat()
            path.lineTo(cx + x, cy + y)
        }
        path.close()
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, moonLight, 0xFFC9C4B0.toInt(), Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
        p.shader = null

        // Cráteres sutiles
        p.color = Color.argb(28, 0, 0, 0)
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.2f, r * 0.16f, p)
        canvas.drawCircle(cx + r * 0.25f, cy + r * 0.3f, r * 0.12f, p)
        canvas.drawCircle(cx + r * 0.1f, cy - r * 0.45f, r * 0.08f, p)
    }
}

/** Mini gráfica de línea con relleno degradado (cripto, divisas). */
class SparklineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var values: List<Double> = emptyList()
        set(v) { field = v; invalidate() }
    var color: Int = Palette.primary

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val v = values
        if (v.size < 2) return
        val min = v.min()
        val max = v.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        val pad = dp(3f)
        fun x(i: Int) = pad + (width - 2 * pad) * i / (v.size - 1)
        fun y(d: Double) = (pad + (height - 2 * pad) * (1 - (d - min) / span)).toFloat()
        path.reset()
        v.forEachIndexed { i, d -> if (i == 0) path.moveTo(x(i), y(d)) else path.lineTo(x(i), y(d)) }
        val area = Path(path).apply { lineTo(x(v.size - 1), height.toFloat()); lineTo(x(0), height.toFloat()); close() }
        fill.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), Palette.alpha(color, 0.3f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)
        line.color = color
        line.strokeWidth = dp(2f)
        canvas.drawPath(path, line)
    }
}

/** Indicador semicircular (índice de calidad del aire). */
class GaugeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var value = 0f
    var max = 100f
    var color = Palette.primary
    var label = ""

    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun set(value: Float, color: Int, label: String) {
        this.value = value; this.color = color; this.label = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val sw = dp(12f)
        val r = minOf(width / 2f, height.toFloat()) - sw
        val rect = RectF(width / 2f - r, sw, width / 2f + r, sw + 2 * r)
        arc.strokeWidth = sw
        arc.color = Color.argb(40, 255, 255, 255)
        canvas.drawArc(rect, 180f, 180f, false, arc)
        arc.color = Palette.alpha(color, 0.25f)
        arc.strokeWidth = sw * 1.8f
        canvas.drawArc(rect, 180f, 180f * (value / max).coerceIn(0f, 1f), false, arc)
        arc.color = color
        arc.strokeWidth = sw
        canvas.drawArc(rect, 180f, 180f * (value / max).coerceIn(0.01f, 1f), false, arc)
        text.typeface = Palette.display(context)
        text.color = Palette.TEXT
        text.textSize = r * 0.5f
        canvas.drawText(label, width / 2f, sw + r - dp(2f), text)
    }
}

/** Barras por hora con colores por tramo y la hora actual resaltada (precio de la luz). */
class HourBarsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var values: List<Pair<Int, Double>> = emptyList()
    var colorOf: (Double) -> Int = { Palette.primary }
    var currentHour = -1
    var showLabels = true

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val max = values.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
        val min = values.minOf { it.second }.coerceAtMost(0.0)
        val labelH = if (showLabels) dp(18f) else 0f
        val h = height - labelH
        val slot = width.toFloat() / 24
        text.typeface = Palette.semibold(context)
        text.textSize = sp(12f)
        values.forEach { (hour, v) ->
            val x = hour * slot
            val bh = ((v - min) / (max - min)).toFloat().coerceIn(0.04f, 1f) * (h - dp(4f))
            val c = colorOf(v)
            p.color = if (hour == currentHour) c else Palette.alpha(c, 0.55f)
            canvas.drawRect(x + slot * 0.15f, h - bh, x + slot * 0.85f, h, p)
            if (hour == currentHour) {
                p.color = Color.WHITE
                canvas.drawRect(x + slot * 0.15f, h - bh - dp(3f), x + slot * 0.85f, h - bh, p)
            }
            if (showLabels && hour % 3 == 0) {
                text.color = if (hour == currentHour) Palette.TEXT else Palette.TEXT_DIM
                canvas.drawText("%02d".format(hour), x + slot / 2, height - dp(3f), text)
            }
        }
    }
}

/** Barra horizontal de nivel (polen). */
class LevelBarView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var fraction = 0f
    var color = Palette.primary
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        p.color = Color.argb(35, 255, 255, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        p.color = color
        canvas.drawRect(0f, 0f, width * fraction.coerceIn(0.02f, 1f), height.toFloat(), p)
    }
}

/** Escala logarítmica suave para niveles de polen (0..1). */
fun pollenFraction(v: Double): Float = (kotlin.math.ln(1 + abs(v)) / kotlin.math.ln(1 + 300.0)).toFloat().coerceIn(0f, 1f)
