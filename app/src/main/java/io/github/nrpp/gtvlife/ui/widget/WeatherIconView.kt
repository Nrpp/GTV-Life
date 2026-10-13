package io.github.nrpp.gtvlife.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import io.github.nrpp.gtvlife.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

/** Iconos meteorológicos de línea, dibujados a mano y animados (sol que gira, lluvia que cae...). */
class WeatherIconView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var code: Int = 0
        set(value) { field = value; invalidate() }
    var isDay: Boolean = true
        set(value) { field = value; invalidate() }
    var animated: Boolean = true

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun set(code: Int, isDay: Boolean) {
        this.code = code
        this.isDay = isDay
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        // Reloj acotado (ciclo de 1 h): con los milisegundos absolutos un Float pierde toda la precisión.
        val t = if (animated) (android.os.SystemClock.uptimeMillis() % 3_600_000L) / 1000f else 0f
        canvas.save()
        canvas.translate((width - s) / 2f, (height - s) / 2f)
        stroke.strokeWidth = s * 0.045f

        val wet = code in 51..67 || code in 80..82 || code >= 95
        val snow = code in 71..77 || code in 85..86
        when {
            code == 0 -> celestial(canvas, s * 0.5f, s * 0.5f, s * 0.24f, t)
            code == 1 -> {
                celestial(canvas, s * 0.42f, s * 0.42f, s * 0.22f, t)
                cloud(canvas, s * 0.66f, s * 0.7f, s * 0.42f)
            }
            code == 2 -> {
                celestial(canvas, s * 0.36f, s * 0.36f, s * 0.2f, t)
                cloud(canvas, s * 0.56f, s * 0.62f, s * 0.7f)
            }
            code == 3 -> {
                cloud(canvas, s * 0.62f, s * 0.42f, s * 0.5f, dim = true)
                cloud(canvas, s * 0.46f, s * 0.6f, s * 0.75f)
            }
            code == 45 || code == 48 -> {
                cloud(canvas, s * 0.5f, s * 0.42f, s * 0.7f)
                fog(canvas, s, t)
            }
            else -> {
                cloud(canvas, s * 0.5f, s * 0.42f, s * 0.8f)
                when {
                    code >= 95 -> { rain(canvas, s, t, 3); bolt(canvas, s, t) }
                    snow -> snow(canvas, s, t)
                    wet -> rain(canvas, s, t, if (code in setOf(65, 67, 82)) 5 else if (code in setOf(51, 53, 55, 56, 57, 80)) 3 else 4)
                }
            }
        }
        canvas.restore()
        if (animated && isShown) postInvalidateDelayed(40)
    }

    private fun glowStroke(canvas: Canvas, p: Path, color: Int) {
        val w = stroke.strokeWidth
        stroke.color = Palette.alpha(color, 0.12f)
        stroke.strokeWidth = w * 3f
        canvas.drawPath(p, stroke)
        stroke.strokeWidth = w
        stroke.color = color
        canvas.drawPath(p, stroke)
    }

    private fun celestial(canvas: Canvas, cx: Float, cy: Float, r: Float, t: Float) {
        if (isDay) {
            val gold = 0xFFFFC94A.toInt()
            fill.color = Palette.alpha(gold, 0.18f)
            canvas.drawCircle(cx, cy, r, fill)
            path.reset(); path.addCircle(cx, cy, r * 0.82f, Path.Direction.CW)
            glowStroke(canvas, path, gold)
            val rot = t * 0.35f
            val w = stroke.strokeWidth
            for (i in 0 until 8) {
                val a = rot + i * (Math.PI / 4).toFloat()
                val x1 = cx + cos(a) * r * 1.2f; val y1 = cy + sin(a) * r * 1.2f
                val x2 = cx + cos(a) * r * 1.55f; val y2 = cy + sin(a) * r * 1.55f
                stroke.color = Palette.alpha(gold, 0.12f); stroke.strokeWidth = w * 3f
                canvas.drawLine(x1, y1, x2, y2, stroke)
                stroke.color = gold; stroke.strokeWidth = w
                canvas.drawLine(x1, y1, x2, y2, stroke)
            }
        } else {
            val moon = 0xFFCFE3FF.toInt()
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            val cut = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW) }
            path.op(cut, Path.Op.DIFFERENCE)
            fill.color = Palette.alpha(moon, 0.15f)
            canvas.drawPath(path, fill)
            glowStroke(canvas, path, moon)
            // Estrellas que parpadean
            fill.color = Palette.alpha(moon, 0.5f + 0.5f * sin(t * 2f))
            canvas.drawCircle(cx + r * 1.2f, cy - r * 0.9f, r * 0.07f, fill)
            fill.color = Palette.alpha(moon, 0.5f + 0.5f * sin(t * 2f + 2f))
            canvas.drawCircle(cx + r * 1.45f, cy - r * 0.2f, r * 0.05f, fill)
        }
    }

    private fun cloud(canvas: Canvas, cx: Float, cy: Float, w: Float, dim: Boolean = false) {
        val h = w * 0.55f
        path.reset()
        val left = cx - w / 2f
        val bottom = cy + h / 2f
        path.addRoundRect(left, bottom - h * 0.5f, left + w, bottom, h * 0.25f, h * 0.25f, Path.Direction.CW)
        val c1 = Path().apply { addCircle(left + w * 0.33f, bottom - h * 0.5f, h * 0.38f, Path.Direction.CW) }
        val c2 = Path().apply { addCircle(left + w * 0.62f, bottom - h * 0.55f, h * 0.5f, Path.Direction.CW) }
        path.op(c1, Path.Op.UNION)
        path.op(c2, Path.Op.UNION)
        fill.color = Palette.BG1
        canvas.drawPath(path, fill)
        fill.color = Palette.alpha(Palette.TEXT, if (dim) 0.04f else 0.08f)
        canvas.drawPath(path, fill)
        glowStroke(canvas, path, Palette.alpha(Palette.TEXT, if (dim) 0.45f else 0.92f))
    }

    private fun rain(canvas: Canvas, s: Float, t: Float, drops: Int) {
        path.reset()
        val len = s * 0.11f
        val span = s * 0.6f
        for (i in 0 until drops) {
            val x = s * 0.5f - span / 2f + span * (i + 0.5f) / drops
            val phase = ((t * 1.6f + i * 0.37f) % 1f)
            val y = s * 0.66f + phase * s * 0.22f
            path.moveTo(x, y)
            path.lineTo(x - len * 0.35f, y + len)
        }
        glowStroke(canvas, path, Palette.primary)
    }

    private fun snow(canvas: Canvas, s: Float, t: Float) {
        for (i in 0 until 5) {
            val x = s * 0.24f + s * 0.52f * i / 4f + sin(t * 1.5f + i) * s * 0.02f
            val phase = ((t * 0.5f + i * 0.29f) % 1f)
            val y = s * 0.68f + phase * s * 0.24f
            fill.color = Palette.alpha(0xFFFFFFFF.toInt(), 1f - phase * 0.6f)
            canvas.drawCircle(x, y, s * 0.028f, fill)
        }
    }

    private fun fog(canvas: Canvas, s: Float, t: Float) {
        path.reset()
        for (i in 0 until 3) {
            val y = s * (0.72f + i * 0.08f)
            val off = sin(t * 0.8f + i) * s * 0.04f
            path.moveTo(s * 0.2f + off, y)
            path.lineTo(s * 0.8f + off, y)
        }
        glowStroke(canvas, path, Palette.TEXT_DIM)
    }

    private fun bolt(canvas: Canvas, s: Float, t: Float) {
        val flash = if ((t % 2.4f) < 0.12f || (t % 2.4f) in 0.22f..0.3f) 1f else 0.75f
        path.reset()
        path.moveTo(s * 0.54f, s * 0.6f)
        path.lineTo(s * 0.44f, s * 0.76f)
        path.lineTo(s * 0.52f, s * 0.76f)
        path.lineTo(s * 0.45f, s * 0.92f)
        fill.color = Palette.alpha(Palette.WARN, 0.3f * flash)
        canvas.drawPath(path, fill)
        glowStroke(canvas, path, Palette.alpha(Palette.WARN, flash))
    }
}
