package io.github.nrpp.gtvlife.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.github.nrpp.gtvlife.ui.theme.Palette
import kotlin.math.sin

/**
 * "Cursor" de la interfaz: un marco luminoso que se desliza hasta el elemento con el foco.
 * Se dibuja encima de todo y puede ocultarse con un fundido cuando no se usa el mando.
 */
class FocusRingView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val current = RectF()
    private val from = RectF()
    private val target = RectF()
    private var hasRect = false
    private var mover: ValueAnimator? = null
    private val path = Path()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.MITER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f * density; strokeCap = Paint.Cap.SQUARE }
    private var cutDp = 18f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun moveTo(rect: RectF, cutDp: Float, animate: Boolean) {
        this.cutDp = cutDp
        if (!animate && mover?.isRunning == true) {
            // Hay una animación en curso: solo se corrige el destino.
            target.set(rect)
            return
        }
        if (!animate && hasRect && rect == current) return
        mover?.cancel()
        if (!hasRect || !animate) {
            current.set(rect)
            target.set(rect)
            hasRect = true
            invalidate()
            return
        }
        from.set(current)
        target.set(rect)
        mover = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                val f = it.animatedValue as Float
                current.set(
                    from.left + (target.left - from.left) * f,
                    from.top + (target.top - from.top) * f,
                    from.right + (target.right - from.right) * f,
                    from.bottom + (target.bottom - from.bottom) * f,
                )
                invalidate()
            }
            start()
        }
    }

    fun clear() {
        hasRect = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!hasRect) return
        val pulse = 0.75f + 0.25f * sin(System.currentTimeMillis() / 380.0).toFloat()
        val r = RectF(current).apply { inset(-4f * density, -4f * density) }
        val cut = cutDp * density + 3f * density
        chamferPath(r, cut, path)

        // Halo: varias pasadas con grosor creciente y alfa decreciente (sin capas por software)
        for (i in 4 downTo 1) {
            glow.strokeWidth = i * 4f * density
            glow.color = Palette.alpha(Palette.primary, 0.07f * pulse)
            canvas.drawPath(path, glow)
        }
        line.color = Palette.alpha(Palette.primary, 0.95f)
        canvas.drawPath(path, line)

        // Esquinas reforzadas
        corner.color = Palette.alpha(Palette.secondary, 0.9f)
        val l = 16f * density
        canvas.drawLine(r.right - l, r.top, r.right, r.top, corner)
        canvas.drawLine(r.right, r.top, r.right, r.top + l, corner)
        canvas.drawLine(r.left, r.bottom - l, r.left, r.bottom, corner)
        canvas.drawLine(r.left, r.bottom, r.left + l, r.bottom, corner)

        if (alpha > 0f && isShown) postInvalidateDelayed(50)
    }
}
