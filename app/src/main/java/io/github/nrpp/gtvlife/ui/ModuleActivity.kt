package io.github.nrpp.gtvlife.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nrpp.gtvlife.data.ModuleId
import io.github.nrpp.gtvlife.ui.modules.ModuleUi
import io.github.nrpp.gtvlife.ui.modules.Modules
import io.github.nrpp.gtvlife.ui.modules.Ticker
import io.github.nrpp.gtvlife.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pantalla completa de un módulo extra. */
class ModuleActivity : BaseActivity() {

    private lateinit var module: ModuleUi<*>
    private lateinit var body: FrameLayout
    private val ticker = Ticker()
    private var lastData: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = ModuleId.from(intent.getStringExtra(EXTRA_ID) ?: "") ?: run { finish(); return }
        module = Modules[id]

        val content = column().apply { setPadding(dp(52), dp(30), dp(52), dp(30)) }
        content.addView(label(id.title))
        content.addView(text(id.title, 36f, Palette.TEXT, Palette.semibold(this)).apply { setPadding(0, dp(8), 0, 0) })
        body = FrameLayout(this)
        content.addView(body, lp(MATCH, WRAP).margins(t = dp(20)))
        val scroll = ScrollView(this).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        setScreen(scroll)

        module.peek(config)?.let(::render) ?: body.addView(text("Cargando…", 22f, Palette.TEXT_DIM))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        load()
                        delay(if (module.live) 60_000L else 10 * 60_000L)
                    }
                }
                launch {
                    while (true) {
                        ticker.run()
                        delay(1000L - System.currentTimeMillis() % 1000L)
                    }
                }
            }
        }
    }

    private suspend fun load() {
        runCatching { module.load(this, config) }
            .onSuccess { if (it != lastData) render(it) }
            .onFailure { e ->
                if (lastData == null) {
                    body.removeAllViews()
                    body.addView(column().apply {
                        gravity = Gravity.START
                        addView(text("No se pudieron cargar los datos", 24f, Palette.WARN, Palette.bold(this@ModuleActivity)))
                        addView(text(friendlyError(e), 18f, Palette.TEXT_DIM), LinearLayout.LayoutParams(WRAP, WRAP).margins(t = dp(8)))
                    })
                }
            }
    }

    private fun render(data: Any) {
        lastData = data
        ticker.clear()
        body.removeAllViews()
        body.addView(module.detailAny(this, config, data, ticker), FrameLayout.LayoutParams(MATCH, WRAP))
        // Primer elemento enfocable, para que el mando "enganche" en la pantalla
        body.post { if (window.decorView.findFocus() == null) firstFocusable(body)?.requestFocus() }
    }

    private fun firstFocusable(v: View): View? {
        if (v.isFocusable && v.tag is FocusSpec) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    override fun preferredFocus(): View? = firstFocusable(body)

    companion object {
        private const val EXTRA_ID = "module"
        fun intent(context: Context, id: ModuleId) = Intent(context, ModuleActivity::class.java).putExtra(EXTRA_ID, id.key)
    }
}
