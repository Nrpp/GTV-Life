package io.github.nrpp.gtvlife.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalEvent
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.data.ConfigStore
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Eventos de hoy y de mañana de todos los calendarios iCal. */
class CalendarActivity : ComponentActivity() {

    private lateinit var config: AppConfig
    private lateinit var content: LinearLayout
    private lateinit var subtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        config = ConfigStore.load(this)
        content = findViewById(R.id.content)
        subtitle = findViewById(R.id.subtitle)
        findViewById<TextView>(R.id.title).text = "Calendario"
        subtitle.text = Fmt.longDate(LocalDate.now())

        if (config.calendars.isEmpty()) {
            content.addView(text("No hay calendarios configurados. Ve a Configuración y escanea el QR.", 24f))
            return
        }
        CalendarRepository.peek()?.let(::render) ?: content.addView(text("Cargando…", 24f))
        lifecycleScope.launch {
            runCatching { CalendarRepository.get(config, force = true) }
                .onSuccess(::render)
                .onFailure {
                    content.removeAllViews()
                    content.addView(text("No se pudieron cargar los calendarios: ${it.message}", 22f, getColor(R.color.warn)))
                }
        }
    }

    private fun render(r: CalendarResult) {
        content.removeAllViews()
        val today = LocalDate.now()
        section("Hoy", today, r.on(today))
        section("Mañana · ${Fmt.shortDate(today.plusDays(1))}", today.plusDays(1), r.on(today.plusDays(1)))
        if (r.errors.isNotEmpty()) {
            content.addView(text("Errores", 22f, getColor(R.color.warn), bold = true).apply { setPadding(0, dp(28), 0, dp(8)) })
            r.errors.forEach { content.addView(text("⚠ $it", 18f, getColor(R.color.warn))) }
        }
        (0 until content.childCount).map { content.getChildAt(it) }.firstOrNull { it.isFocusable }?.requestFocus()
    }

    private fun section(label: String, day: LocalDate, events: List<CalEvent>) {
        content.addView(text(label, 26f, getColor(R.color.accent), bold = true).apply {
            setPadding(0, if (content.childCount == 0) 0 else dp(28), 0, dp(12))
        })
        if (events.isEmpty()) {
            content.addView(text("Sin eventos", 20f, getColor(R.color.text_dim)).apply { setPadding(dp(4), 0, 0, 0) })
            return
        }
        events.forEach { content.addView(row(it, day)) }
    }

    private fun row(e: CalEvent, day: LocalDate) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = getDrawable(R.drawable.row_bg)
        isFocusable = true
        setPadding(dp(20), dp(14), dp(20), dp(14))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(10) }

        addView(colorDot(context, e.color, 16))
        addView(text(Fmt.eventTime(e, day, config), 22f, getColor(R.color.text_dim)).apply {
            minWidth = dp(220)
            setPadding(dp(18), 0, dp(18), 0)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(e.title, 24f))
            val sub = listOfNotNull(e.location, e.calendarName.takeIf { it.isNotBlank() }).joinToString("  ·  ")
            if (sub.isNotEmpty()) addView(text(sub, 17f, getColor(R.color.text_dim)))
        })
    }
}
