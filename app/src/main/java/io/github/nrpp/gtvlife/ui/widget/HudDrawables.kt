package io.github.nrpp.gtvlife.ui.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import io.github.nrpp.gtvlife.ui.theme.Palette

/** Contorno con dos esquinas recortadas (arriba-izquierda y abajo-derecha), típico de una interfaz HUD. */
fun chamferPath(r: RectF, cut: Float, out: Path = Path()): Path = out.apply {
    reset()
    val c = cut.coerceAtMost(minOf(r.width(), r.height()) / 3f)
    moveTo(r.left + c, r.top)
    lineTo(r.right, r.top)
    lineTo(r.right, r.bottom - c)
    lineTo(r.right - c, r.bottom)
    lineTo(r.left, r.bottom)
    lineTo(r.left, r.top + c)
    close()
}

/**
 * Panel de cristal: relleno translúcido, borde degradado con el color de acento y detalles de esquina.
 * [strong] = panel destacado (borde y tinte más intensos).
 */
class HudPanelDrawable(
    private val density: Float,
    private val cutDp: Float = 18f,
    private val strong: Boolean = false,
    private val tint: Int? = null,
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.SQUARE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var shaderAccent = 0

    private fun color() = tint ?: Palette.primary

    private fun buildShaders() {
        val b = bounds
        val c = color()
        fill.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            intArrayOf(
                Palette.alpha(c, if (strong) 0.14f else 0.075f),
                Color.argb(if (strong) 20 else 14, 255, 255, 255),
                Color.argb(6, 255, 255, 255),
            ),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
        stroke.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            intArrayOf(
                Palette.alpha(c, if (strong) 0.9f else 0.55f),
                Color.argb(18, 255, 255, 255),
                Palette.alpha(Palette.secondary, if (strong) 0.6f else 0.3f),
            ),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        shaderAccent = c
    }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        buildShaders()
    }

    override fun draw(canvas: Canvas) {
        if (shaderAccent != color()) buildShaders()
        val half = density / 2f
        rect.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half)
        val cut = cutDp * density
        chamferPath(rect, cut, path)
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)

        // Detalle en la esquina recortada y marca superior
        val c = cut.coerceAtMost(minOf(rect.width(), rect.height()) / 3f)
        accentPaint.color = color()
        accentPaint.strokeWidth = 2.5f * density
        canvas.drawLine(rect.left, rect.top + c, rect.left + c, rect.top, accentPaint)
        accentPaint.strokeWidth = 2f * density
        canvas.drawLine(rect.left + c + 6 * density, rect.top, rect.left + c + 34 * density, rect.top, accentPaint)

        // Tres puntos en la esquina superior derecha
        dotPaint.color = Palette.alpha(color(), 0.7f)
        for (i in 0 until 3) {
            canvas.drawRect(
                rect.right - (10 + i * 7) * density, rect.top + 6 * density,
                rect.right - (7 + i * 7) * density, rect.top + 9 * density, dotPaint,
            )
        }
    }

    override fun setAlpha(alpha: Int) { fill.alpha = alpha; stroke.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { fill.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Pastilla con relleno suave y borde fino, para chips de información y botones pequeños. */
class ChipDrawable(private val density: Float, private val color: Int, private val filled: Float = 0.14f) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val half = density / 2f
        rect.set(bounds.left + half, bounds.top + half, bounds.right - half, bounds.bottom - half)
        chamferPath(rect, rect.height() / 3.2f, path)
        fill.color = Palette.alpha(color, filled)
        stroke.color = Palette.alpha(color, 0.55f)
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
