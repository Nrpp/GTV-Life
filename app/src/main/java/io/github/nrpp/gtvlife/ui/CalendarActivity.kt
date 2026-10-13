package io.github.nrpp.gtvlife.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.data.CalEvent
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.HudPanelDrawable
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Línea de tiempo de hoy y agenda de los próximos días. */
class CalendarActivity : BaseActivity() {

    private lateinit var title: android.widget.TextView
    private lateinit var summary: LinearLayout
    private lateinit var today: LinearLayout
    private lateinit var upcoming: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column().apply { setPadding(dp(52), dp(30), dp(52), dp(26)) }

        val header = row(Gravity.BOTTOM)
        val left = column()
        left.addView(label("Calendario"))
        title = text(Fmt.longDate(LocalDate.now()), 36f, Palette.TEXT, Palette.semibold(this)).apply { setPadding(0, dp(8), 0, 0) }
        left.addView(title)
        header.addView(left, lp(0, WRAP, 1f))
        summary = row(Gravity.END)
        header.addView(summary)
        root.addView(header)

        val body = row(Gravity.TOP)
        today = column()
        upcoming = column()
        body.addView(panel("Hoy", today), lp(0, MATCH, 1.35f).margins(r = dp(22)))
        body.addView(panel("Próximos días", upcoming), lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f).margins(t = dp(22)))
        setScreen(root)

        if (config.calendars.isEmpty()) {
            today.addView(text("No hay calendarios configurados. Ve a Ajustes y escanea el QR.", 22f, Palette.TEXT_DIM))
            return
        }
        CalendarRepository.peek()?.let(::render) ?: today.addView(text("Cargando…", 22f, Palette.TEXT_DIM))
        lifecycleScope.launch {
            runCatching { CalendarRepository.get(config) }
                .onSuccess(::render)
                .onFailure {
                    today.removeAllViews()
                    today.addView(text("No se pudieron cargar los calendarios: ${it.message}", 20f, Palette.WARN))
                }
        }
    }

    private fun panel(name: String, content: LinearLayout): View {
        val p = column().apply {
            background = HudPanelDrawable(resources.displayMetrics.density)
            setPadding(dp(22), dp(18), dp(22), dp(10))
        }
        p.addView(label(name))
        val scroll = ScrollView(this).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(30))
            clipToPadding = false
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        p.addView(scroll, lp(MATCH, 0, 1f).margins(t = dp(10)))
        return p
    }

    private fun render(r: CalendarResult) {
        val day = LocalDate.now()
        val now = Instant.now()
        val events = r.on(day)

        summary.removeAllViews()
        summary.addView(chip(if (events.isEmpty()) "DÍA LIBRE" else "${events.size} HOY", Palette.primary, 14f).apply { typeface = Palette.display(this@CalendarActivity) })
        val next = r.next(now)
        if (next != null) summary.addView(chip("PRÓXIMO ${Fmt.until(next.start, now).uppercase(Fmt.ES)}", Palette.secondary, 14f).apply {
            typeface = Palette.display(this@CalendarActivity)
        }, lp(WRAP, WRAP).margins(l = dp(12)))

        // Hoy: línea de tiempo
        today.removeAllViews()
        if (events.isEmpty()) {
            today.addView(text("Sin eventos. Día libre ✨", 22f, Palette.TEXT_DIM, Palette.semibold(this)))
        }
        val allDay = events.filter { it.allDay }
        val timed = events.filter { !it.allDay }
        allDay.forEach { today.addView(timelineRow(it, day, now, State.ALLDAY, first = today.childCount == 0, last = false)) }
        var markerAdded = false
        timed.forEachIndexed { i, e ->
            val state = when {
                e.end <= now && e.end > e.start -> State.PAST
                e.start <= now && e.end > now -> State.NOW
                else -> State.FUTURE
            }
            if (!markerAdded && state == State.FUTURE) {
                today.addView(nowMarker())
                markerAdded = true
            }
            today.addView(timelineRow(e, day, now, state, first = today.childCount == 0, last = i == timed.lastIndex))
            if (state == State.NOW) markerAdded = true
        }
        if (!markerAdded && timed.isNotEmpty()) today.addView(nowMarker())

        // Próximos días
        upcoming.removeAllViews()
        var any = false
        for (offset in 1L..6L) {
            val d = day.plusDays(offset)
            val list = r.on(d)
            if (list.isEmpty()) continue
            any = true
            upcoming.addView(text((if (offset == 1L) "MAÑANA  ·  " else "") + Fmt.shortDate(d).uppercase(Fmt.ES), 12f, Palette.primary, Palette.display(this), 0.18f).apply {
                setPadding(0, if (upcoming.childCount == 0) 0 else dp(16), 0, dp(8))
            })
            list.forEach { upcoming.addView(agendaRow(it, d)) }
        }
        if (!any) upcoming.addView(text("Nada en los próximos días", 19f, Palette.TEXT_DIM))

        if (r.errors.isNotEmpty()) {
            upcoming.addView(text("ERRORES", 12f, Palette.WARN, Palette.display(this), 0.18f).apply { setPadding(0, dp(18), 0, dp(6)) })
            r.errors.forEach { upcoming.addView(text("⚠  $it", 16f, Palette.WARN)) }
        }

        (0 until today.childCount).map { today.getChildAt(it) }.firstOrNull { it.isFocusable }?.requestFocus()
            ?: (0 until upcoming.childCount).map { upcoming.getChildAt(it) }.firstOrNull { it.isFocusable }?.requestFocus()
    }

    private enum class State { PAST, NOW, FUTURE, ALLDAY }

    private fun nowMarker() = row().apply {
        setPadding(0, dp(4), 0, dp(4))
        addView(text("AHORA", 11f, Palette.primary, Palette.display(this@CalendarActivity), 0.2f), lp(dp(80), WRAP))
        addView(View(this@CalendarActivity).apply { setBackgroundColor(Palette.primary) }, lp(0, dp(2), 1f))
    }

    private fun timelineRow(e: CalEvent, day: LocalDate, now: Instant, state: State, first: Boolean, last: Boolean): View {
        val row = row(Gravity.TOP).apply {
            hudFocus(8f, 1.02f)
            setPadding(0, 0, dp(10), 0)
            if (state == State.PAST) alpha = 0.45f
            if (state == State.NOW) background = HudPanelDrawable(resources.displayMetrics.density, 8f, strong = true)
        }
        row.addView(RailView(this, e.color, state == State.NOW, first, last), lp(dp(26), MATCH))
        val timeText = if (e.allDay) "TODO EL DÍA" else Fmt.eventTime(e, day, config)
        row.addView(text(timeText, 14f, if (state == State.NOW) Palette.primary else Palette.TEXT_DIM, Palette.display(this), 0.06f).apply {
            setPadding(0, dp(14), dp(14), 0)
        }, lp(dp(170), WRAP))
        val col = column().apply { setPadding(0, dp(11), 0, dp(12)) }
        col.addView(text(e.title, 22f, Palette.TEXT, Palette.bold(this)).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        val sub = listOfNotNull(e.location, e.calendarName.takeIf { it.isNotBlank() }).joinToString("  ·  ")
        if (sub.isNotEmpty()) col.addView(text(sub, 15f, Palette.TEXT_DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0) })
        if (state == State.NOW) {
            val total = Duration.between(e.start, e.end).toMillis().coerceAtLeast(1)
            val done = Duration.between(e.start, now).toMillis().coerceIn(0, total)
            val bar = FrameLayout(this).apply { setBackgroundColor(Color.argb(40, 255, 255, 255)) }
            bar.addView(View(this).apply { setBackgroundColor(Palette.primary) }, FrameLayout.LayoutParams(0, MATCH))
            bar.post { bar.getChildAt(0).layoutParams.width = (bar.width * done / total).toInt(); bar.requestLayout() }
            col.addView(bar, lp(MATCH, dp(4)).margins(t = dp(10)))
            col.addView(text(Fmt.remaining(e.end, now).uppercase(Fmt.ES), 11f, Palette.primary, Palette.display(this), 0.15f).apply { setPadding(0, dp(6), 0, 0) })
        } else if (state == State.FUTURE) {
            col.addView(text(Fmt.until(e.start, now).uppercase(Fmt.ES), 11f, Palette.TEXT_FAINT, Palette.display(this), 0.15f).apply { setPadding(0, dp(5), 0, 0) })
        }
        row.addView(col, lp(0, WRAP, 1f))
        return row
    }

    private fun agendaRow(e: CalEvent, day: LocalDate) = row(Gravity.TOP).apply {
        hudFocus(8f, 1.02f)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(colorDot(this@CalendarActivity, e.color, 10).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(4)
        })
        val col = column()
        col.addView(text(if (e.allDay) "TODO EL DÍA" else Fmt.eventTime(e, day, config), 12f, Palette.TEXT_DIM, Palette.display(this@CalendarActivity), 0.06f))
        col.addView(text(e.title, 20f, Palette.TEXT, Palette.semibold(this@CalendarActivity)).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(4), 0, 0)
        })
        addView(col, lp(0, WRAP, 1f).margins(l = dp(14)))
    }

    /** Raíl vertical de la línea de tiempo con un nodo del color del calendario. */
    private class RailView(context: Context, colorHex: String, private val active: Boolean, private val first: Boolean, private val last: Boolean) : View(context) {
        private val color = runCatching { Color.parseColor(colorHex) }.getOrDefault(Palette.primary)
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = 22 * d
            p.color = Color.argb(50, 255, 255, 255)
            p.strokeWidth = 2 * d
            canvas.drawLine(cx, if (first) cy else 0f, cx, if (last) cy else height.toFloat(), p)
            if (active) {
                p.color = Palette.alpha(color, 0.3f)
                canvas.drawCircle(cx, cy, 11 * d, p)
            }
            p.color = color
            canvas.drawCircle(cx, cy, 6 * d, p)
            p.color = Palette.BG0
            canvas.drawCircle(cx, cy, 2.5f * d, p)
        }
    }
}
