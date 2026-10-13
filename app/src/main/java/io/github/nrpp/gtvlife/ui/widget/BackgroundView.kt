package io.github.nrpp.gtvlife.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import io.github.nrpp.gtvlife.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo futurista: degradado profundo, auroras del color de acento, suelo de rejilla en perspectiva,
 * textura de grano, líneas de escaneo, viñeta y partículas flotando muy despacio.
 * La parte estática se pinta una sola vez en un bitmap; solo las partículas se animan (~30 fps).
 */
class BackgroundView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var texture: Bitmap? = null
    private var textureAccent: Int = 0
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density

    var animateParticles = true

    private class Particle(var x: Float, var y: Float, val r: Float, val speed: Float, val phase: Float, val tinted: Boolean)

    private var particles: List<Particle> = emptyList()
    private var lastFrame = 0L

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        texture?.recycle()
        texture = null
        val rnd = Random(7)
        particles = List(38) {
            Particle(
                x = rnd.nextFloat() * w, y = rnd.nextFloat() * h,
                r = (0.8f + rnd.nextFloat() * 1.8f) * density,
                speed = (4f + rnd.nextFloat() * 10f) * density,
                phase = rnd.nextFloat() * 2f * PI.toFloat(),
                tinted = rnd.nextFloat() < 0.6f,
            )
        }
    }

    /** Vuelve a pintar la textura (p. ej. tras cambiar el color de acento). */
    fun refresh() {
        texture?.recycle()
        texture = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        if (texture == null || textureAccent != Palette.primary) {
            texture?.recycle()
            texture = buildTexture(width, height)
            textureAccent = Palette.primary
        }
        canvas.drawBitmap(texture!!, 0f, 0f, bitmapPaint)

        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame).coerceAtMost(100)) / 1000f
        lastFrame = now
        val t = (now % 3_600_000L) / 1000f
        for (p in particles) {
            if (animateParticles) {
                p.y -= p.speed * dt
                if (p.y < -10f) { p.y = height + 10f; p.x = Random.nextFloat() * width }
            }
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.3f + p.phase))
            val sway = sin(t * 0.4f + p.phase) * 6f * density
            particlePaint.color = Palette.alpha(if (p.tinted) Palette.primary else Color.WHITE, 0.55f * twinkle)
            canvas.drawCircle(p.x + sway, p.y, p.r, particlePaint)
        }
        if (animateParticles && isShown) postInvalidateDelayed(33)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        texture?.recycle()
        texture = null
    }

    private fun buildTexture(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val wf = w.toFloat()
        val hf = h.toFloat()

        // Degradado base
        p.shader = LinearGradient(0f, 0f, 0f, hf, intArrayOf(Palette.BG0, Palette.BG1, Palette.BG0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p)

        // Auroras
        fun glow(cx: Float, cy: Float, r: Float, color: Int, a: Float) {
            p.shader = RadialGradient(cx, cy, r, intArrayOf(Palette.alpha(color, a), Palette.alpha(color, a * 0.35f), Color.TRANSPARENT), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, wf, hf, p)
        }
        glow(wf * 0.12f, -hf * 0.15f, wf * 0.65f, Palette.primary, 0.20f)
        glow(wf * 0.98f, hf * 1.08f, wf * 0.6f, Palette.secondary, 0.18f)
        glow(wf * 0.62f, hf * 0.35f, wf * 0.35f, Palette.secondary, 0.05f)

        // Suelo de rejilla en perspectiva
        val horizon = hf * 0.64f
        p.shader = LinearGradient(0f, horizon, 0f, hf, Color.TRANSPARENT, Palette.alpha(Palette.primary, 0.16f), Shader.TileMode.CLAMP)
        p.strokeWidth = max(1f, density * 0.7f)
        p.style = Paint.Style.STROKE
        val vx = wf / 2f
        for (i in -24..24) {
            val bottomX = vx + i * wf / 14f
            c.drawLine(vx + i * wf / 160f, horizon, bottomX, hf, p)
        }
        var y = horizon
        var step = 3f * density
        while (y < hf) {
            c.drawLine(0f, y, wf, y, p)
            step *= 1.32f
            y += step
        }
        p.style = Paint.Style.FILL

        // Grano
        val noise = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val rnd = Random(42)
        noise.setPixels(IntArray(160 * 160) {
            val v = rnd.nextInt(256)
            Color.argb(rnd.nextInt(14), v, v, v)
        }, 0, 160, 0, 0, 160, 160)
        p.shader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRect(0f, 0f, wf, hf, p)

        // Líneas de escaneo
        val lines = Bitmap.createBitmap(1, 4, Bitmap.Config.ARGB_8888)
        lines.setPixels(intArrayOf(Color.argb(22, 0, 0, 0), 0, 0, Color.argb(10, 255, 255, 255)), 0, 1, 0, 0, 1, 4)
        p.shader = BitmapShader(lines, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRect(0f, 0f, wf, hf, p)

        // Viñeta
        p.shader = RadialGradient(wf / 2f, hf / 2f, max(wf, hf) * 0.75f, intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 0)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf, p)

        noise.recycle()
        lines.recycle()
        return bmp
    }
}
