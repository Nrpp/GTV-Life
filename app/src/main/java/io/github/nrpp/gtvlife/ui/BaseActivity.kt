package io.github.nrpp.gtvlife.ui

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.ui.theme.Accent
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.BackgroundView
import io.github.nrpp.gtvlife.ui.widget.FocusRingView

/** Cómo se resalta un elemento al recibir el foco: recorte del marco y zoom. */
data class FocusSpec(val cutDp: Float, val zoom: Float)

fun View.hudFocus(cutDp: Float = 18f, zoom: Float = 1.03f) {
    isFocusable = true
    tag = FocusSpec(cutDp, zoom)
}

/**
 * Base de todas las pantallas: fondo con textura, cursor luminoso que sigue al foco y
 * ocultación automática del cursor tras unos segundos sin tocar el mando.
 */
abstract class BaseActivity : ComponentActivity() {

    protected lateinit var config: AppConfig
    protected open val useBackground = true
    protected open val cursorIdleMs = 6_000L

    protected var background: BackgroundView? = null
    private var ring: FocusRingView? = null
    private lateinit var root: FrameLayout

    protected var lastInteraction = SystemClock.uptimeMillis()
        private set
    protected var cursorHidden = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideCursor() }
    private val tmpRect = Rect()

    override fun onCreate(savedInstanceState: Bundle?) {
        config = ConfigStore.load(this)
        Palette.accent = Accent.from(config.accent)
        super.onCreate(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        val fresh = ConfigStore.load(this)
        val accentChanged = fresh.accent != config.accent
        config = fresh
        if (accentChanged) {
            Palette.accent = Accent.from(config.accent)
            recreate()
            return
        }
        onConfigLoaded()
    }

    /** Se llama en cada onStart con la configuración recién leída. */
    protected open fun onConfigLoaded() {}

    override fun onResume() {
        super.onResume()
        markInteraction()
        // Garantiza que siempre haya un elemento con el foco (si no, el mando no "engancha").
        if (::root.isInitialized) root.post {
            val f = focused()
            if (f == null || f.tag !is FocusSpec) (preferredFocus() ?: root).requestFocus()
            focused()?.let { v ->
                val z = specOf(v).zoom
                if (!cursorHidden && v.scaleX != z) v.animate().scaleX(z).scaleY(z).setDuration(200).start()
            }
            syncRing()
        }
    }

    /** Elemento que debe recibir el foco al entrar en la pantalla, si no lo tiene ninguno. */
    protected open fun preferredFocus(): View? = null

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(hideRunnable)
    }

    fun setScreen(content: View) {
        root = FrameLayout(this)
        if (useBackground) {
            background = BackgroundView(this).also { root.addView(it, MATCH) }
        }
        root.addView(content, MATCH)
        ring = FocusRingView(this).also { root.addView(it, MATCH) }
        setContentView(root)

        root.viewTreeObserver.addOnGlobalFocusChangeListener { old, new -> onFocusMoved(old, new) }
        root.viewTreeObserver.addOnScrollChangedListener { syncRing() }
        root.viewTreeObserver.addOnGlobalLayoutListener { syncRing() }
    }

    fun setScreen(layoutRes: Int) = setScreen(layoutInflater.inflate(layoutRes, FrameLayout(this), false))

    private fun focused(): View? = if (::root.isInitialized) root.findFocus() else null

    private fun specOf(v: View) = v.tag as? FocusSpec ?: FocusSpec(10f, 1f)

    private fun rectFor(v: View): RectF {
        tmpRect.set(0, 0, v.width, v.height)
        root.offsetDescendantRectToMyCoords(v, tmpRect)
        val spec = specOf(v)
        val dx = v.width * (spec.zoom - 1f) / 2f
        val dy = v.height * (spec.zoom - 1f) / 2f
        return RectF(tmpRect.left - dx, tmpRect.top - dy, tmpRect.right + dx, tmpRect.bottom + dy)
    }

    private fun onFocusMoved(old: View?, new: View?) {
        old?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(160)?.start()
        val r = ring ?: return
        if (new == null || new.width == 0) { r.clear(); return }
        val spec = specOf(new)
        if (!cursorHidden) new.animate().scaleX(spec.zoom).scaleY(spec.zoom).setDuration(200).start()
        r.moveTo(rectFor(new), spec.cutDp, animate = true)
    }

    /** Recoloca el cursor sin animación (scroll, cambios de tamaño...). */
    fun syncRing() {
        val r = ring ?: return
        val v = focused() ?: return
        if (v.width == 0) return
        r.moveTo(rectFor(v), specOf(v).cutDp, animate = false)
    }

    // ---- Ocultar el cursor cuando no se usa el mando ----

    protected fun markInteraction() {
        lastInteraction = SystemClock.uptimeMillis()
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, cursorIdleMs)
    }

    private fun hideCursor() {
        if (cursorHidden) return
        cursorHidden = true
        ring?.animate()?.alpha(0f)?.setDuration(700)?.start()
        focused()?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(700)?.start()
        onCursorVisibilityChanged(false)
    }

    private fun showCursor() {
        if (!cursorHidden) return
        cursorHidden = false
        ring?.animate()?.alpha(1f)?.setDuration(150)?.start()
        focused()?.let { v ->
            val s = specOf(v)
            v.animate().scaleX(s.zoom).scaleY(s.zoom).setDuration(150).start()
        }
        syncRing()
        ring?.invalidate()
        onCursorVisibilityChanged(true)
    }

    protected open fun onCursorVisibilityChanged(visible: Boolean) {}

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val wasHidden = cursorHidden
            markInteraction()
            if (wasHidden) {
                showCursor()
                // La primera pulsación solo despierta el cursor, para que se vea dónde está.
                if (event.keyCode in WAKE_ONLY_KEYS && focused() != null) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        private val MATCH = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        private val WAKE_ONLY_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        )
    }
}
