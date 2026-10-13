package io.github.nrpp.gtvlife.ui.modules

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.nrpp.gtvlife.data.AirQuality
import io.github.nrpp.gtvlife.data.AirRepository
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.data.Coin
import io.github.nrpp.gtvlife.data.Countdown
import io.github.nrpp.gtvlife.data.CryptoRepository
import io.github.nrpp.gtvlife.data.CurrencyRepository
import io.github.nrpp.gtvlife.data.ElectricityDay
import io.github.nrpp.gtvlife.data.ElectricityRepository
import io.github.nrpp.gtvlife.data.HistoryEvent
import io.github.nrpp.gtvlife.data.ModuleId
import io.github.nrpp.gtvlife.data.Moon
import io.github.nrpp.gtvlife.data.MoonInfo
import io.github.nrpp.gtvlife.data.NewsItem
import io.github.nrpp.gtvlife.data.NewsRepository
import io.github.nrpp.gtvlife.data.Note
import io.github.nrpp.gtvlife.data.OnThisDayRepository
import io.github.nrpp.gtvlife.data.Quotes
import io.github.nrpp.gtvlife.data.Rate
import io.github.nrpp.gtvlife.data.TimerState
import io.github.nrpp.gtvlife.data.Weather
import io.github.nrpp.gtvlife.data.WeatherCodes
import io.github.nrpp.gtvlife.data.WeatherRepository
import io.github.nrpp.gtvlife.data.WorldCities
import io.github.nrpp.gtvlife.data.shortWeekday
import io.github.nrpp.gtvlife.ui.BaseActivity
import io.github.nrpp.gtvlife.ui.Fmt
import io.github.nrpp.gtvlife.ui.MATCH
import io.github.nrpp.gtvlife.ui.WRAP
import io.github.nrpp.gtvlife.ui.chip
import io.github.nrpp.gtvlife.ui.colorDot
import io.github.nrpp.gtvlife.ui.column
import io.github.nrpp.gtvlife.ui.dp
import io.github.nrpp.gtvlife.ui.hudFocus
import io.github.nrpp.gtvlife.ui.label
import io.github.nrpp.gtvlife.ui.lp
import io.github.nrpp.gtvlife.ui.margins
import io.github.nrpp.gtvlife.ui.row
import io.github.nrpp.gtvlife.ui.text
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.ChipDrawable
import io.github.nrpp.gtvlife.ui.widget.GaugeView
import io.github.nrpp.gtvlife.ui.widget.HourBarsView
import io.github.nrpp.gtvlife.ui.widget.HudPanelDrawable
import io.github.nrpp.gtvlife.ui.widget.LevelBarView
import io.github.nrpp.gtvlife.ui.widget.MoonView
import io.github.nrpp.gtvlife.ui.widget.SparklineView
import io.github.nrpp.gtvlife.ui.widget.WeatherIconView
import io.github.nrpp.gtvlife.ui.widget.pollenFraction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Funciones que se llaman cada segundo mientras la pantalla está visible (módulos "en vivo"). */
class Ticker {
    private val fns = mutableListOf<() -> Unit>()
    fun every(f: () -> Unit) { fns += f; f() }
    fun run() = fns.forEach { it() }
    fun clear() = fns.clear()
}

/**
 * Un módulo: cómo se cargan sus datos, cómo se ve su tarjeta en el menú y su pantalla completa.
 */
abstract class ModuleUi<T : Any>(val id: ModuleId) {
    /** Se refresca cada segundo (reloj, temporizador...). */
    open val live = false
    abstract suspend fun load(ctx: Context, c: AppConfig): T
    open fun peek(c: AppConfig): T? = null
    abstract fun tile(a: BaseActivity, c: AppConfig, d: T): View
    abstract fun detail(a: BaseActivity, c: AppConfig, d: T, tick: Ticker): View

    @Suppress("UNCHECKED_CAST")
    fun tileAny(a: BaseActivity, c: AppConfig, d: Any) = tile(a, c, d as T)

    @Suppress("UNCHECKED_CAST")
    fun detailAny(a: BaseActivity, c: AppConfig, d: Any, tick: Ticker) = detail(a, c, d as T, tick)
}

object Modules {
    val ALL: Map<ModuleId, ModuleUi<*>> = listOf(
        ForecastModule, AirModule, PollenModule, MoonModule, WorldClocksModule, CountdownModule,
        QuoteModule, OnThisDayModule, NewsModule, CryptoModule, CurrencyModule, NotesModule,
        TimerModule, ElectricityModule, MonthModule,
    ).associateBy { it.id }

    operator fun get(id: ModuleId) = ALL.getValue(id)
}

// ---- Piezas comunes ----------------------------------------------------------------------------

internal fun Context.panel(strong: Boolean = false, cut: Float = 14f, focusable: Boolean = true) = column().apply {
    background = HudPanelDrawable(resources.displayMetrics.density, cut, strong)
    setPadding(dp(20), dp(16), dp(20), dp(16))
    if (focusable) hudFocus(cut, 1.02f)
}

internal fun Context.stat(name: String, value: String, color: Int = Palette.primary) = column().apply {
    background = ChipDrawable(resources.displayMetrics.density, color, 0.06f)
    setPadding(dp(16), dp(10), dp(16), dp(10))
    addView(text(name, 10f, Palette.TEXT_DIM, Palette.display(context), 0.18f))
    addView(text(value, 22f, Palette.TEXT, Palette.semibold(context)).apply { setPadding(0, dp(4), 0, 0) })
}

internal fun Context.big(value: String, sp: Float, color: Int = Palette.TEXT) =
    text(value, sp, color, Palette.display(this)).apply { setShadowLayer(dp(14).toFloat(), 0f, 0f, Palette.alpha(Palette.primary, 0.35f)) }

internal fun Context.dim(value: CharSequence, sp: Float = 15f) = text(value, sp, Palette.TEXT_DIM, Palette.medium(this))

internal fun TextView.lines(n: Int) = apply { maxLines = n; ellipsize = TextUtils.TruncateAt.END }

internal fun Context.grid(views: List<View>, perRow: Int, gapDp: Int = 16): LinearLayout {
    val col = column()
    views.chunked(perRow).forEachIndexed { r, chunk ->
        val row = row(Gravity.TOP)
        chunk.forEachIndexed { i, v ->
            row.addView(v, lp(0, WRAP, 1f).margins(r = if (i < perRow - 1) dp(gapDp) else 0))
        }
        repeat(perRow - chunk.size) { i ->
            row.addView(View(this), lp(0, 0, 1f).margins(r = if (chunk.size + i < perRow - 1) dp(gapDp) else 0))
        }
        col.addView(row, lp(MATCH, WRAP).margins(t = if (r == 0) 0 else dp(gapDp)))
    }
    return col
}

private fun pct(v: Double?) = v?.let { (if (it >= 0) "▲ " else "▼ ") + "%.1f %%".format(Fmt.ES, abs(it)) } ?: "—"
private fun pctColor(v: Double?) = when {
    v == null -> Palette.TEXT_DIM
    v >= 0 -> Palette.OK
    else -> Palette.DANGER
}

private fun money(v: Double): String = when {
    v >= 1000 -> "%,.0f €".format(Fmt.ES, v)
    v >= 1 -> "%,.2f €".format(Fmt.ES, v)
    else -> "%.4f €".format(Fmt.ES, v)
}

// ---- 1. Previsión 7 días ----------------------------------------------------------------------

object ForecastModule : ModuleUi<Weather>(ModuleId.FORECAST) {
    override suspend fun load(ctx: Context, c: AppConfig): Weather {
        if (!c.hasLocation) error("Configura tu ubicación")
        return WeatherRepository.get(c)
    }

    override fun peek(c: AppConfig) = WeatherRepository.peek()

    override fun tile(a: BaseActivity, c: AppConfig, d: Weather): View = a.row(Gravity.TOP).apply {
        d.days.take(4).forEachIndexed { i, day ->
            addView(a.column().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(a.text(if (i == 0) "HOY" else shortWeekday(day.date), 10f, Palette.TEXT_DIM, Palette.display(a), 0.1f))
                addView(WeatherIconView(a).apply { set(day.code, true); animated = false }, lp(a.dp(36), a.dp(36)).margins(t = a.dp(4)))
                addView(a.text(Fmt.temp(day.max), 17f, Palette.TEXT, Palette.semibold(a)))
                addView(a.text(Fmt.temp(day.min), 14f, Palette.TEXT_DIM, Palette.medium(a)))
            }, lp(0, WRAP, 1f))
        }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: Weather, tick: Ticker): View {
        val rainy = d.days.count { it.precipProb >= 50 }
        val col = a.column()
        col.addView(a.dim("${c.locationName.substringBefore(",")}  ·  ${if (rainy == 0) "sin lluvia prevista esta semana" else "lluvia probable $rainy ${if (rainy == 1) "día" else "días"}"}", 19f))
        val cards = d.days.mapIndexed { i, day ->
            a.panel(strong = i == 0).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(a.dp(10), a.dp(14), a.dp(10), a.dp(14))
                addView(a.text(if (i == 0) "HOY" else shortWeekday(day.date), 13f, Palette.primary, Palette.display(a), 0.15f))
                addView(a.dim(day.date.dayOfMonth.toString() + " " + day.date.month.getDisplayName(java.time.format.TextStyle.SHORT, Fmt.ES).removeSuffix("."), 14f))
                addView(WeatherIconView(a).apply { set(day.code, true); animated = i == 0 }, lp(a.dp(72), a.dp(72)).margins(t = a.dp(8)))
                addView(a.text(WeatherCodes.describe(day.code), 14f, Palette.TEXT, Palette.semibold(a)).apply { gravity = Gravity.CENTER; minLines = 2 }.lines(2), lp(MATCH, WRAP))
                addView(a.text(Fmt.temp(day.max), 30f, Palette.TEXT, Palette.display(a)), lp(WRAP, WRAP).margins(t = a.dp(6)))
                addView(a.text(Fmt.temp(day.min), 20f, Palette.TEXT_DIM, Palette.display(a)), lp(WRAP, WRAP))
                addView(a.text("💧 ${day.precipProb}%", 15f, if (day.precipProb >= 50) Palette.WARN else Palette.TEXT_DIM, Palette.semibold(a)), lp(WRAP, WRAP).margins(t = a.dp(8)))
            }
        }
        col.addView(a.grid(cards, 7, 12), lp(MATCH, WRAP).margins(t = a.dp(18)))
        return col
    }
}

// ---- 2. Calidad del aire ----------------------------------------------------------------------

object AirModule : ModuleUi<AirQuality>(ModuleId.AIR) {
    override suspend fun load(ctx: Context, c: AppConfig) = AirRepository.get(c)
    override fun peek(c: AppConfig) = AirRepository.peek(c)

    override fun tile(a: BaseActivity, c: AppConfig, d: AirQuality): View = a.column().apply {
        val (name, color) = d.level
        addView(GaugeView(a).apply { set((d.aqi ?: 0).toFloat(), color, d.aqi?.toString() ?: "—") }, lp(MATCH, a.dp(78)))
        addView(a.text(name.uppercase(Fmt.ES), 13f, color, Palette.display(a), 0.12f).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).margins(t = a.dp(4)))
        d.pm25?.let { addView(a.dim("PM2,5  ${it.roundToInt()} µg/m³", 14f).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).margins(t = a.dp(4))) }
    }

    private fun advice(aqi: Int?) = when (aqi ?: -1) {
        in 0..39 -> "Buen momento para ventilar la casa y hacer deporte al aire libre."
        in 40..59 -> "Aire aceptable. Las personas sensibles deberían moderar el ejercicio intenso en el exterior."
        in 60..79 -> "Evita el ejercicio intenso al aire libre y ventila en las horas de menos tráfico."
        -1 -> "No hay datos disponibles para tu ubicación."
        else -> "Calidad del aire muy mala: reduce la actividad en el exterior y mantén las ventanas cerradas."
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: AirQuality, tick: Ticker): View = a.row(Gravity.TOP).apply {
        val (name, color) = d.level
        addView(a.panel(strong = true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(a.label("Índice europeo (EAQI)"))
            addView(GaugeView(a).apply { set((d.aqi ?: 0).toFloat(), color, d.aqi?.toString() ?: "—") }, lp(a.dp(340), a.dp(180)).margins(t = a.dp(14)))
            addView(a.text(name, 34f, color, Palette.bold(a)), lp(WRAP, WRAP).margins(t = a.dp(6)))
            addView(a.dim(advice(d.aqi), 18f).apply { gravity = Gravity.CENTER }, lp(a.dp(380), WRAP).margins(t = a.dp(10)))
        }, lp(0, WRAP, 1.1f).margins(r = a.dp(22)))
        val stats = listOf(
            "PM2,5" to d.pm25, "PM10" to d.pm10, "OZONO (O₃)" to d.ozone, "DIÓXIDO DE NITRÓGENO" to d.no2,
        ).map { (k, v) -> a.stat(k, v?.let { "${it.roundToInt()} µg/m³" } ?: "—") }
        addView(a.column().apply {
            addView(a.label("Contaminantes"))
            addView(a.grid(stats, 2, 12), lp(MATCH, WRAP).margins(t = a.dp(14)))
            addView(a.dim("Fuente: Copernicus (CAMS) vía Open-Meteo. Valores actuales en tu ubicación.", 14f), lp(MATCH, WRAP).margins(t = a.dp(16)))
        }, lp(0, WRAP, 1f))
    }
}

// ---- 3. Polen ---------------------------------------------------------------------------------

object PollenModule : ModuleUi<AirQuality>(ModuleId.POLLEN) {
    override suspend fun load(ctx: Context, c: AppConfig) = AirRepository.get(c)
    override fun peek(c: AppConfig) = AirRepository.peek(c)

    private fun row(a: BaseActivity, name: String, v: Double, big: Boolean) = a.column().apply {
        val (lvl, color) = AirQuality.pollenLevel(v)
        val head = a.row()
        head.addView(a.text(name, if (big) 22f else 15f, Palette.TEXT, Palette.semibold(a)), lp(0, WRAP, 1f))
        head.addView(a.text(lvl.uppercase(Fmt.ES), if (big) 13f else 10f, color, Palette.display(a), 0.1f))
        addView(head)
        addView(LevelBarView(a).apply { fraction = pollenFraction(v); this.color = color }, lp(MATCH, a.dp(if (big) 10 else 5)).margins(t = a.dp(5)))
        if (big) addView(a.dim("${v.roundToInt()} granos/m³", 14f), lp(WRAP, WRAP).margins(t = a.dp(4)))
    }

    override fun tile(a: BaseActivity, c: AppConfig, d: AirQuality): View = a.column().apply {
        if (d.pollen.isEmpty()) {
            addView(a.dim("Sin datos de polen para tu zona (solo Europa)", 15f))
            return@apply
        }
        d.pollen.sortedByDescending { it.second }.take(3).forEach { (n, v) -> addView(row(a, n, v, false), lp(MATCH, WRAP).margins(b = a.dp(8))) }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: AirQuality, tick: Ticker): View = a.column().apply {
        if (d.pollen.isEmpty()) {
            addView(a.dim("No hay previsión de polen para tu ubicación. Los datos de polen (CAMS) solo cubren Europa.", 20f))
            return@apply
        }
        val worst = d.pollen.maxBy { it.second }
        addView(a.dim("Más alto ahora: ${worst.first} (${AirQuality.pollenLevel(worst.second).first.lowercase(Fmt.ES)})", 20f))
        val cards = d.pollen.sortedByDescending { it.second }.map { (n, v) -> a.panel().apply { addView(row(a, n, v, true)) } }
        addView(a.grid(cards, 3, 16), lp(MATCH, WRAP).margins(t = a.dp(18)))
        addView(a.dim("Fuente: Copernicus (CAMS) vía Open-Meteo.", 14f), lp(MATCH, WRAP).margins(t = a.dp(16)))
    }
}

// ---- 4. Fase lunar ----------------------------------------------------------------------------

object MoonModule : ModuleUi<MoonInfo>(ModuleId.MOON) {
    override suspend fun load(ctx: Context, c: AppConfig) = Moon.at()
    override fun peek(c: AppConfig) = Moon.at()

    override fun tile(a: BaseActivity, c: AppConfig, d: MoonInfo): View = a.column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        addView(MoonView(a).apply { phase = d.phase }, lp(a.dp(78), a.dp(78)))
        addView(a.text(d.name, 17f, Palette.TEXT, Palette.bold(a)).lines(1), lp(WRAP, WRAP).margins(t = a.dp(6)))
        addView(a.dim("${(d.illumination * 100).roundToInt()} % iluminada", 14f))
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: MoonInfo, tick: Ticker): View = a.row().apply {
        addView(MoonView(a).apply { phase = d.phase }, lp(a.dp(300), a.dp(300)))
        val today = LocalDate.now()
        fun days(to: LocalDate) = ChronoUnit.DAYS.between(today, to).let { if (it == 0L) "hoy" else if (it == 1L) "mañana" else "en $it días" }
        addView(a.column().apply {
            addView(a.text(d.name, 44f, Palette.TEXT, Palette.bold(a)))
            addView(a.dim("${(d.illumination * 100).roundToInt()} % iluminada  ·  ${"%.1f".format(Fmt.ES, d.ageDays)} días de lunación", 20f), lp(WRAP, WRAP).margins(t = a.dp(6)))
            val stats = listOf(
                a.stat("PRÓXIMA LUNA LLENA", "${Fmt.shortDate(d.nextFull)} (${days(d.nextFull)})"),
                a.stat("PRÓXIMA LUNA NUEVA", "${Fmt.shortDate(d.nextNew)} (${days(d.nextNew)})"),
            )
            addView(a.grid(stats, 1, 12), lp(MATCH, WRAP).margins(t = a.dp(24)))
        }, lp(0, WRAP, 1f).margins(l = a.dp(48)))
    }
}

// ---- 5. Relojes del mundo ---------------------------------------------------------------------

object WorldClocksModule : ModuleUi<List<String>>(ModuleId.WORLD_CLOCKS) {
    override val live = true
    override suspend fun load(ctx: Context, c: AppConfig) = c.worldClocks
    override fun peek(c: AppConfig) = c.worldClocks
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun dayLabel(zone: ZoneId): String {
        val diff = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.now(zone))
        return when (diff) { 0L -> "Hoy"; 1L -> "Mañana"; -1L -> "Ayer"; else -> "" }
    }

    private fun offset(zone: ZoneId): String {
        val now = Instant.now()
        val mins = (zone.rules.getOffset(now).totalSeconds - ZoneId.systemDefault().rules.getOffset(now).totalSeconds) / 60
        if (mins == 0) return "misma hora"
        val h = abs(mins) / 60
        val m = abs(mins) % 60
        return (if (mins > 0) "+" else "−") + (if (m == 0) "$h h" else "$h h $m min")
    }

    override fun tile(a: BaseActivity, c: AppConfig, d: List<String>): View = a.column().apply {
        if (d.isEmpty()) addView(a.dim("Añade ciudades desde el móvil"))
        d.take(4).forEach { z ->
            val zone = ZoneId.of(z)
            addView(a.row().apply {
                addView(a.text(WorldCities.name(z), 16f, Palette.TEXT, Palette.semibold(a)).lines(1), lp(0, WRAP, 1f))
                addView(a.text(fmt.format(ZonedDateTime.now(zone)), 18f, Palette.TEXT, Palette.display(a)))
            }, lp(MATCH, WRAP).margins(b = a.dp(7)))
        }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<String>, tick: Ticker): View {
        val cards = d.map { z ->
            val zone = ZoneId.of(z)
            val time = a.big("", 54f)
            val icon = WeatherIconView(a).apply { animated = false }
            val sub = a.dim("", 16f)
            tick.every {
                val now = ZonedDateTime.now(zone)
                time.text = fmt.format(now)
                icon.set(0, now.hour in 7..20)
                sub.text = listOf(dayLabel(zone), offset(zone)).filter { it.isNotEmpty() }.joinToString("  ·  ")
            }
            a.panel().apply {
                val head = a.row()
                head.addView(a.text(WorldCities.name(z), 22f, Palette.TEXT, Palette.bold(a)).lines(1), lp(0, WRAP, 1f))
                head.addView(icon, lp(a.dp(34), a.dp(34)))
                addView(head)
                addView(time, lp(WRAP, WRAP).margins(t = a.dp(6)))
                addView(sub)
            }
        }
        return if (cards.isEmpty()) a.dim("Añade ciudades desde la configuración del móvil.", 20f) else a.grid(cards, 3, 16)
    }
}

// ---- 6. Cuenta atrás --------------------------------------------------------------------------

object CountdownModule : ModuleUi<List<Countdown>>(ModuleId.COUNTDOWN) {
    override suspend fun load(ctx: Context, c: AppConfig) = sorted(c)
    override fun peek(c: AppConfig) = sorted(c)

    private fun sorted(c: AppConfig) = c.countdowns.sortedBy { it.next() }.filter { !it.next().isBefore(LocalDate.now()) }

    private fun days(cd: Countdown) = ChronoUnit.DAYS.between(LocalDate.now(), cd.next())

    private fun daysText(n: Long) = when (n) { 0L -> "¡HOY!"; 1L -> "1"; else -> n.toString() }

    override fun tile(a: BaseActivity, c: AppConfig, d: List<Countdown>): View = a.column().apply {
        val first = d.firstOrNull()
        if (first == null) {
            addView(a.dim("Añade fechas importantes desde el móvil"))
            return@apply
        }
        val n = days(first)
        val r = a.row(Gravity.BOTTOM)
        r.addView(a.big(daysText(n), if (n == 0L) 34f else 46f, Palette.primary))
        if (n > 0) r.addView(a.text(if (n == 1L) "DÍA" else "DÍAS", 12f, Palette.TEXT_DIM, Palette.display(a), 0.15f), lp(WRAP, WRAP).margins(l = a.dp(8), b = a.dp(8)))
        addView(r)
        addView(a.text(first.name, 18f, Palette.TEXT, Palette.bold(a)).lines(1))
        d.getOrNull(1)?.let { addView(a.dim("Después: ${it.name} (${days(it)} d)", 14f).lines(1), lp(MATCH, WRAP).margins(t = a.dp(6))) }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<Countdown>, tick: Ticker): View {
        if (d.isEmpty()) return a.dim("No hay cuentas atrás. Añádelas desde la configuración del móvil (cumpleaños, vacaciones, viajes...).", 20f)
        val cards = d.map { cd ->
            val n = days(cd)
            a.panel(strong = n <= 7).apply {
                val r = a.row(Gravity.BOTTOM)
                r.addView(a.big(daysText(n), 54f, if (n <= 7) Palette.primary else Palette.TEXT))
                if (n > 0) r.addView(a.text(if (n == 1L) "DÍA" else "DÍAS", 13f, Palette.TEXT_DIM, Palette.display(a), 0.15f), lp(WRAP, WRAP).margins(l = a.dp(10), b = a.dp(10)))
                addView(r)
                addView(a.text(cd.name, 22f, Palette.TEXT, Palette.bold(a)).lines(2))
                addView(a.dim(Fmt.longDate(cd.next()) + if (cd.yearly) "  ·  cada año" else "", 15f))
            }
        }
        return a.grid(cards, 3, 16)
    }
}

// ---- 7. Frase del día -------------------------------------------------------------------------

object QuoteModule : ModuleUi<Pair<String, String>>(ModuleId.QUOTE) {
    override suspend fun load(ctx: Context, c: AppConfig) = Quotes.of()
    override fun peek(c: AppConfig) = Quotes.of()

    override fun tile(a: BaseActivity, c: AppConfig, d: Pair<String, String>): View = a.column().apply {
        addView(a.text("“", 34f, Palette.primary, Palette.display(a)), lp(WRAP, a.dp(26)))
        addView(a.text(d.first, 16f, Palette.TEXT, Palette.semibold(a)).lines(4))
        addView(a.dim("— ${d.second}", 14f).lines(1), lp(WRAP, WRAP).margins(t = a.dp(6)))
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: Pair<String, String>, tick: Ticker): View = a.column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(a.dp(60), a.dp(30), a.dp(60), 0)
        addView(a.text("“", 90f, Palette.primary, Palette.display(a)), lp(WRAP, a.dp(80)))
        addView(a.text(d.first, 40f, Palette.TEXT, Palette.semibold(a)).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.1f) })
        addView(a.text("— ${d.second}", 22f, Palette.primary, Palette.display(a), 0.08f), lp(WRAP, WRAP).margins(t = a.dp(26)))
        addView(a.dim("Una frase nueva cada día", 15f), lp(WRAP, WRAP).margins(t = a.dp(40)))
    }
}

// ---- 8. Tal día como hoy ----------------------------------------------------------------------

object OnThisDayModule : ModuleUi<List<HistoryEvent>>(ModuleId.ON_THIS_DAY) {
    override suspend fun load(ctx: Context, c: AppConfig) = OnThisDayRepository.get()
    override fun peek(c: AppConfig) = OnThisDayRepository.peek()

    override fun tile(a: BaseActivity, c: AppConfig, d: List<HistoryEvent>): View = a.column().apply {
        // Cambia de efeméride cada 10 minutos
        val e = d.getOrNull(((System.currentTimeMillis() / 600_000) % d.size.coerceAtLeast(1)).toInt()) ?: return@apply
        addView(a.big(e.year.toString(), 26f, Palette.primary))
        addView(a.text(e.text, 15f, Palette.TEXT, Palette.medium(a)).lines(4), lp(MATCH, WRAP).margins(t = a.dp(4)))
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<HistoryEvent>, tick: Ticker): View = a.column().apply {
        addView(a.dim("Lo que pasó un ${LocalDate.now().dayOfMonth} de ${LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, Fmt.ES)} (Wikipedia)", 19f))
        d.forEach { e ->
            addView(a.row(Gravity.TOP).apply {
                hudFocus(8f, 1.01f)
                setPadding(a.dp(10), a.dp(12), a.dp(10), a.dp(12))
                addView(a.big(e.year.toString(), 24f, Palette.primary), lp(a.dp(110), WRAP))
                addView(a.text(e.text, 20f, Palette.TEXT, Palette.medium(a)), lp(0, WRAP, 1f).margins(l = a.dp(10)))
            }, lp(MATCH, WRAP).margins(t = a.dp(6)))
        }
    }
}

// ---- 9. Noticias ------------------------------------------------------------------------------

object NewsModule : ModuleUi<List<NewsItem>>(ModuleId.NEWS) {
    override suspend fun load(ctx: Context, c: AppConfig) = NewsRepository.get(c)
    override fun peek(c: AppConfig) = NewsRepository.peek(c)

    override fun tile(a: BaseActivity, c: AppConfig, d: List<NewsItem>): View = a.column().apply {
        d.take(3).forEach { n ->
            addView(a.row(Gravity.TOP).apply {
                addView(View(a).apply { setBackgroundColor(Palette.primary) }, lp(a.dp(3), a.dp(30)).margins(r = a.dp(8), t = a.dp(2)))
                addView(a.text(n.title, 14f, Palette.TEXT, Palette.semibold(a)).lines(2), lp(0, WRAP, 1f))
            }, lp(MATCH, WRAP).margins(b = a.dp(6)))
        }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<NewsItem>, tick: Ticker): View = a.column().apply {
        d.forEach { n ->
            addView(a.column().apply {
                hudFocus(8f, 1.01f)
                setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(12))
                addView(a.text(listOfNotNull(n.source.ifBlank { null }?.uppercase(Fmt.ES), n.date?.let { Fmt.ago(it.toEpochMilli()).uppercase(Fmt.ES) }).joinToString("  ·  "), 11f, Palette.primary, Palette.display(a), 0.12f))
                addView(a.text(n.title, 23f, Palette.TEXT, Palette.bold(a)), lp(MATCH, WRAP).margins(t = a.dp(4)))
                if (n.summary.isNotBlank()) addView(a.dim(n.summary, 17f).lines(3), lp(MATCH, WRAP).margins(t = a.dp(4)))
            }, lp(MATCH, WRAP).margins(b = a.dp(6)))
        }
    }
}

// ---- 10. Criptomonedas ------------------------------------------------------------------------

object CryptoModule : ModuleUi<List<Coin>>(ModuleId.CRYPTO) {
    override suspend fun load(ctx: Context, c: AppConfig) = CryptoRepository.get(c)
    override fun peek(c: AppConfig) = CryptoRepository.peek(c)

    override fun tile(a: BaseActivity, c: AppConfig, d: List<Coin>): View = a.column().apply {
        d.take(4).forEach { coin ->
            addView(a.row().apply {
                addView(a.text(coin.symbol, 13f, Palette.primary, Palette.display(a), 0.06f), lp(a.dp(50), WRAP))
                addView(a.text(money(coin.price), 16f, Palette.TEXT, Palette.semibold(a)).lines(1), lp(0, WRAP, 1f))
                addView(a.text(pct(coin.change24h), 13f, pctColor(coin.change24h), Palette.semibold(a)))
            }, lp(MATCH, WRAP).margins(b = a.dp(8)))
        }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<Coin>, tick: Ticker): View {
        val cards = d.map { coin ->
            a.panel().apply {
                val head = a.row()
                head.addView(a.text(coin.name, 22f, Palette.TEXT, Palette.bold(a)).lines(1), lp(0, WRAP, 1f))
                head.addView(a.text(coin.symbol, 13f, Palette.primary, Palette.display(a), 0.1f))
                addView(head)
                addView(a.big(money(coin.price), 34f), lp(WRAP, WRAP).margins(t = a.dp(6)))
                addView(a.row().apply {
                    addView(a.text("24 h  ${pct(coin.change24h)}", 15f, pctColor(coin.change24h), Palette.semibold(a)))
                    addView(a.text("7 d  ${pct(coin.change7d)}", 15f, pctColor(coin.change7d), Palette.semibold(a)), lp(WRAP, WRAP).margins(l = a.dp(16)))
                }, lp(WRAP, WRAP).margins(t = a.dp(4)))
                addView(SparklineView(a).apply { values = coin.sparkline; color = pctColor(coin.change7d) }, lp(MATCH, a.dp(70)).margins(t = a.dp(10)))
            }
        }
        return a.column().apply {
            addView(a.grid(cards, 3, 16))
            addView(a.dim("Precios en euros. Gráfica de los últimos 7 días. Datos: CoinGecko.", 14f), lp(MATCH, WRAP).margins(t = a.dp(14)))
        }
    }
}

// ---- 11. Divisas ------------------------------------------------------------------------------

object CurrencyModule : ModuleUi<List<Rate>>(ModuleId.CURRENCY) {
    override suspend fun load(ctx: Context, c: AppConfig) = CurrencyRepository.get(c)
    override fun peek(c: AppConfig) = CurrencyRepository.peek(c)

    private fun rate(v: Double) = if (v >= 100) "%.2f".format(Fmt.ES, v) else "%.4f".format(Fmt.ES, v)

    override fun tile(a: BaseActivity, c: AppConfig, d: List<Rate>): View = a.column().apply {
        addView(a.dim("1 € =", 13f), lp(WRAP, WRAP).margins(b = a.dp(4)))
        d.take(4).forEach { r ->
            addView(a.row().apply {
                addView(a.text(r.code, 13f, Palette.primary, Palette.display(a), 0.06f), lp(a.dp(50), WRAP))
                addView(a.text(rate(r.rate), 16f, Palette.TEXT, Palette.semibold(a)), lp(0, WRAP, 1f))
                addView(a.text(if ((r.change ?: 0.0) >= 0) "▲" else "▼", 12f, pctColor(r.change)))
            }, lp(MATCH, WRAP).margins(b = a.dp(6)))
        }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<Rate>, tick: Ticker): View {
        val cards = d.map { r ->
            a.panel().apply {
                val head = a.row()
                head.addView(a.text(CurrencyRepository.NAMES[r.code] ?: r.code, 20f, Palette.TEXT, Palette.bold(a)).lines(1), lp(0, WRAP, 1f))
                head.addView(a.text(r.code, 13f, Palette.primary, Palette.display(a), 0.1f))
                addView(head)
                addView(a.big("1 € = ${rate(r.rate)}", 28f), lp(WRAP, WRAP).margins(t = a.dp(6)))
                addView(a.text("Ayer  ${pct(r.change)}", 15f, pctColor(r.change), Palette.semibold(a)), lp(WRAP, WRAP).margins(t = a.dp(4)))
                addView(SparklineView(a).apply { values = r.history; color = Palette.primary }, lp(MATCH, a.dp(60)).margins(t = a.dp(8)))
            }
        }
        return a.column().apply {
            addView(a.grid(cards, 3, 16))
            addView(a.dim("Tipos de referencia del Banco Central Europeo (último mes). Datos: Frankfurter.", 14f), lp(MATCH, WRAP).margins(t = a.dp(14)))
        }
    }
}

// ---- 12. Notas y tareas -----------------------------------------------------------------------

object NotesModule : ModuleUi<List<Note>>(ModuleId.NOTES) {
    override suspend fun load(ctx: Context, c: AppConfig) = c.notes
    override fun peek(c: AppConfig) = c.notes

    override fun tile(a: BaseActivity, c: AppConfig, d: List<Note>): View = a.column().apply {
        val pending = d.filter { !it.done }
        addView(a.text(if (d.isEmpty()) "SIN NOTAS" else "${pending.size} PENDIENTE${if (pending.size == 1) "" else "S"}", 13f, Palette.primary, Palette.display(a), 0.12f))
        if (d.isEmpty()) addView(a.dim("Escríbelas desde el móvil", 15f), lp(WRAP, WRAP).margins(t = a.dp(6)))
        pending.take(4).forEach { n ->
            addView(a.text("☐  ${n.text}", 15f, Palette.TEXT, Palette.medium(a)).lines(1), lp(MATCH, WRAP).margins(t = a.dp(6)))
        }
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: List<Note>, tick: Ticker): View = a.column().apply {
        if (d.isEmpty()) {
            addView(a.dim("No hay notas. Escríbelas desde la configuración del móvil; aquí podrás marcarlas como hechas.", 20f))
            return@apply
        }
        addView(a.dim("Pulsa OK para marcar o desmarcar una tarea", 16f), lp(WRAP, WRAP).margins(b = a.dp(10)))
        d.forEachIndexed { i, n ->
            val box = a.text("", 26f, Palette.primary, Palette.bold(a))
            val label = a.text(n.text, 22f, Palette.TEXT, Palette.semibold(a))
            fun paint(done: Boolean) {
                box.text = if (done) "☑" else "☐"
                label.setTextColor(if (done) Palette.TEXT_FAINT else Palette.TEXT)
                label.paintFlags = if (done) label.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else label.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            paint(n.done)
            addView(a.row().apply {
                hudFocus(8f, 1.01f)
                isClickable = true
                setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(10))
                addView(box)
                addView(label, lp(0, WRAP, 1f).margins(l = a.dp(14)))
                setOnClickListener {
                    val cfg = a.currentConfig()
                    val notes = cfg.notes.toMutableList()
                    val idx = notes.indexOfFirst { it.text == n.text }.takeIf { it >= 0 } ?: i
                    if (idx >= notes.size) return@setOnClickListener
                    val updated = notes[idx].copy(done = !notes[idx].done)
                    notes[idx] = updated
                    a.saveConfig(cfg.copy(notes = notes), invalidate = false)
                    paint(updated.done)
                }
            }, lp(MATCH, WRAP).margins(b = a.dp(4)))
        }
    }
}

// ---- 13. Temporizador y cronómetro ------------------------------------------------------------

object TimerModule : ModuleUi<TimerState>(ModuleId.TIMER) {
    override val live = true
    override suspend fun load(ctx: Context, c: AppConfig) = TimerState
    override fun peek(c: AppConfig) = TimerState

    override fun tile(a: BaseActivity, c: AppConfig, d: TimerState): View = a.column().apply {
        addView(a.text(if (d.mode == TimerState.Mode.TIMER) "TEMPORIZADOR" else "CRONÓMETRO", 12f, Palette.TEXT_DIM, Palette.display(a), 0.15f))
        addView(a.big(d.format(d.displayMs()), 40f, if (d.running) Palette.primary else Palette.TEXT), lp(WRAP, WRAP).margins(t = a.dp(8)))
        addView(a.text(if (d.running) "● EN MARCHA" else if (d.ringing) "⏰ ¡TIEMPO!" else "PULSA PARA USAR", 11f, if (d.running) Palette.OK else Palette.TEXT_FAINT, Palette.display(a), 0.12f), lp(WRAP, WRAP).margins(t = a.dp(8)))
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: TimerState, tick: Ticker): View = a.column().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        fun button(textValue: String, color: Int = Palette.primary, onClick: () -> Unit) = a.chip(textValue, color, 15f).apply {
            typeface = Palette.display(a)
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(12))
            isClickable = true
            hudFocus(8f, 1.06f)
            setOnClickListener { onClick(); tick.run() }
        }
        val modes = a.row(Gravity.CENTER)
        val timerTab = button("TEMPORIZADOR") { d.switchMode(TimerState.Mode.TIMER) }
        val swTab = button("CRONÓMETRO") { d.switchMode(TimerState.Mode.STOPWATCH) }
        modes.addView(timerTab)
        modes.addView(swTab, lp(WRAP, WRAP).margins(l = a.dp(14)))
        addView(modes)

        val time = a.big("", 130f)
        addView(time, lp(WRAP, WRAP).margins(t = a.dp(10)))
        val bar = FrameLayout(a).apply { setBackgroundColor(Color.argb(40, 255, 255, 255)) }
        val fill = View(a).apply { setBackgroundColor(Palette.primary) }
        bar.addView(fill, FrameLayout.LayoutParams(0, MATCH))
        addView(bar, lp(a.dp(560), a.dp(6)).margins(t = a.dp(4)))

        val controls = a.row(Gravity.CENTER)
        val minus = button("− 1 MIN") { d.add(-60_000) }
        val plus1 = button("+ 1 MIN") { d.add(60_000) }
        val plus5 = button("+ 5 MIN") { d.add(5 * 60_000) }
        val play = button("▶  INICIAR", Palette.OK) { d.toggle() }
        val reset = button("↺  REINICIAR", Palette.WARN) { d.reset() }
        listOf(minus, plus1, plus5, play, reset).forEachIndexed { i, b -> controls.addView(b, lp(WRAP, WRAP).margins(l = if (i == 0) 0 else a.dp(12))) }
        addView(controls, lp(WRAP, WRAP).margins(t = a.dp(26)))
        addView(a.dim("Cuando el temporizador llegue a cero sonará un aviso en cualquier pantalla de la app.", 15f), lp(WRAP, WRAP).margins(t = a.dp(20)))

        tick.every {
            val isTimer = d.mode == TimerState.Mode.TIMER
            time.text = d.format(d.displayMs())
            time.setTextColor(if (d.running) Palette.primary else Palette.TEXT)
            timerTab.alpha = if (isTimer) 1f else 0.45f
            swTab.alpha = if (isTimer) 0.45f else 1f
            listOf(minus, plus1, plus5).forEach { it.visibility = if (isTimer) View.VISIBLE else View.GONE }
            bar.visibility = if (isTimer) View.VISIBLE else View.INVISIBLE
            val f = if (d.timerSetMs > 0) d.displayMs().toFloat() / d.timerSetMs else 0f
            fill.layoutParams.width = (bar.width * f.coerceIn(0f, 1f)).toInt()
            fill.requestLayout()
            play.text = if (d.running) "❚❚  PAUSA" else "▶  INICIAR"
        }
        post { play.requestFocus() }
    }
}

// ---- 14. Precio de la luz ---------------------------------------------------------------------

object ElectricityModule : ModuleUi<ElectricityDay>(ModuleId.ELECTRICITY) {
    override suspend fun load(ctx: Context, c: AppConfig) = ElectricityRepository.get(c)
    override fun peek(c: AppConfig) = ElectricityRepository.peek(c)

    private fun kwh(v: Double) = "%.3f".format(Fmt.ES, v)
    private fun level(d: ElectricityDay, v: Double) = when (d.color(v)) {
        0xFF3DFFA2.toInt() -> "BARATO"
        0xFFFFD447.toInt() -> "MEDIO"
        else -> "CARO"
    }

    override fun tile(a: BaseActivity, c: AppConfig, d: ElectricityDay): View = a.column().apply {
        val now = d.at(LocalTime.now().hour) ?: return@apply
        val color = d.color(now.eurKwh)
        val r = a.row(Gravity.BOTTOM)
        r.addView(a.big(kwh(now.eurKwh), 34f, color))
        r.addView(a.dim("€/kWh", 13f), lp(WRAP, WRAP).margins(l = a.dp(6), b = a.dp(6)))
        addView(r)
        addView(a.text("AHORA  ·  ${level(d, now.eurKwh)}", 11f, color, Palette.display(a), 0.12f), lp(WRAP, WRAP).margins(t = a.dp(2)))
        d.cheapest?.let { addView(a.dim("Más barata: %02d-%02d h".format(it.hour, it.hour + 1), 14f), lp(WRAP, WRAP).margins(t = a.dp(8))) }
        addView(HourBarsView(a).apply {
            values = d.prices.map { it.hour to it.eurKwh }; colorOf = d::color; currentHour = LocalTime.now().hour; showLabels = false
        }, lp(MATCH, a.dp(30)).margins(t = a.dp(6)))
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: ElectricityDay, tick: Ticker): View = a.column().apply {
        val hour = LocalTime.now().hour
        val now = d.at(hour)
        val top = a.row(Gravity.TOP)
        top.addView(a.panel(strong = true).apply {
            addView(a.label("Ahora · %02d-%02d h".format(hour, hour + 1)))
            if (now != null) {
                val color = d.color(now.eurKwh)
                val r = a.row(Gravity.BOTTOM)
                r.addView(a.big(kwh(now.eurKwh), 64f, color))
                r.addView(a.dim("€/kWh", 18f), lp(WRAP, WRAP).margins(l = a.dp(10), b = a.dp(12)))
                addView(r, lp(WRAP, WRAP).margins(t = a.dp(8)))
                addView(a.text("PRECIO ${level(d, now.eurKwh)}", 14f, color, Palette.display(a), 0.15f))
            }
        }, lp(0, WRAP, 1f).margins(r = a.dp(18)))
        val stats = listOfNotNull(
            d.cheapest?.let { a.stat("MÁS BARATA", "%02d-%02d h · %s".format(it.hour, it.hour + 1, kwh(it.eurKwh)), Palette.OK) },
            d.priciest?.let { a.stat("MÁS CARA", "%02d-%02d h · %s".format(it.hour, it.hour + 1, kwh(it.eurKwh)), Palette.DANGER) },
            a.stat("MEDIA DEL DÍA", "${kwh(d.average)} €/kWh"),
        )
        top.addView(a.grid(stats, 1, 10), lp(0, WRAP, 1f))
        addView(top)
        addView(a.panel().apply {
            addView(a.label("Precio por horas · hoy"))
            addView(HourBarsView(a).apply {
                values = d.prices.map { it.hour to it.eurKwh }; colorOf = d::color; currentHour = hour
            }, lp(MATCH, a.dp(170)).margins(t = a.dp(12)))
            val cheap = d.prices.filter { it.hour >= hour }.sortedBy { it.eurKwh }.take(3).sortedBy { it.hour }
            if (cheap.isNotEmpty()) addView(a.text("Mejores horas que quedan hoy: " + cheap.joinToString(", ") { "%02d h".format(it.hour) }, 18f, Palette.OK, Palette.semibold(a)), lp(WRAP, WRAP).margins(t = a.dp(10)))
        }, lp(MATCH, WRAP).margins(t = a.dp(18)))
        addView(a.dim("Tarifa regulada PVPC 2.0TD (${if (c.electricityZone == "CYM") "Ceuta y Melilla" else "Península, Baleares y Canarias"}), impuestos no incluidos. Datos: Red Eléctrica.", 14f), lp(MATCH, WRAP).margins(t = a.dp(12)))
    }
}

// ---- 15. Calendario del mes -------------------------------------------------------------------

object MonthModule : ModuleUi<CalendarResult>(ModuleId.MONTH) {
    override suspend fun load(ctx: Context, c: AppConfig): CalendarResult =
        if (c.calendars.isEmpty()) CalendarResult(emptyList(), emptyList()) else CalendarRepository.get(c)

    override fun peek(c: AppConfig) = CalendarRepository.peek() ?: if (c.calendars.isEmpty()) CalendarResult(emptyList(), emptyList()) else null

    private val headers = listOf("L", "M", "X", "J", "V", "S", "D")

    private fun days(month: LocalDate): List<LocalDate?> {
        val first = month.withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1
        val count = month.lengthOfMonth()
        return List(lead) { null } + (1..count).map { first.withDayOfMonth(it) }
    }

    private fun monthTitle(d: LocalDate) =
        d.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Fmt.ES).replaceFirstChar { it.titlecase(Fmt.ES) } + " " + d.year

    override fun tile(a: BaseActivity, c: AppConfig, d: CalendarResult): View = a.column().apply {
        val today = LocalDate.now()
        addView(a.text(monthTitle(today).uppercase(Fmt.ES), 11f, Palette.TEXT_DIM, Palette.display(a), 0.12f))
        val cells = days(today)
        val grid = a.column()
        grid.addView(a.row().apply { headers.forEach { h -> addView(a.text(h, 9f, Palette.TEXT_FAINT, Palette.display(a)).apply { gravity = Gravity.CENTER }, lp(0, WRAP, 1f)) } })
        cells.chunked(7).forEach { week ->
            grid.addView(a.row().apply {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    val t = a.text(day?.dayOfMonth?.toString() ?: "", 11f, if (day != null && day.isBefore(today)) Palette.TEXT_FAINT else Palette.TEXT, Palette.semibold(a)).apply { gravity = Gravity.CENTER }
                    if (day == today) {
                        t.setTextColor(Palette.BG0)
                        t.background = ChipDrawable(a.resources.displayMetrics.density, Palette.primary, 1f)
                    } else if (day != null && d.on(day).isNotEmpty()) {
                        t.setTextColor(Palette.primary)
                    }
                    addView(t, lp(0, a.dp(16), 1f))
                }
            })
        }
        addView(grid, lp(MATCH, WRAP).margins(t = a.dp(4)))
    }

    override fun detail(a: BaseActivity, c: AppConfig, d: CalendarResult, tick: Ticker): View = a.row(Gravity.TOP).apply {
        val today = LocalDate.now()
        val side = a.column()
        fun showDay(day: LocalDate) {
            side.removeAllViews()
            side.addView(a.label(if (day == today) "Hoy" else Fmt.shortDate(day)))
            side.addView(a.text(Fmt.longDate(day), 24f, Palette.TEXT, Palette.bold(a)), lp(WRAP, WRAP).margins(t = a.dp(8)))
            val events = d.on(day)
            if (events.isEmpty()) side.addView(a.dim(if (c.calendars.isEmpty()) "Añade calendarios para ver aquí tus eventos" else "Sin eventos", 18f), lp(WRAP, WRAP).margins(t = a.dp(12)))
            events.forEach { e ->
                side.addView(a.row(Gravity.TOP).apply {
                    addView(colorDot(a, e.color, 10).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = a.dp(6) })
                    addView(a.column().apply {
                        addView(a.text(if (e.allDay) "TODO EL DÍA" else Fmt.eventTime(e, day, c), 12f, Palette.TEXT_DIM, Palette.display(a), 0.06f))
                        addView(a.text(e.title, 19f, Palette.TEXT, Palette.semibold(a)).lines(2))
                    }, lp(0, WRAP, 1f).margins(l = a.dp(12)))
                }, lp(MATCH, WRAP).margins(t = a.dp(12)))
            }
        }

        val gridPanel = a.panel(focusable = false)
        gridPanel.addView(a.label(monthTitle(today)))
        val grid = a.column()
        grid.addView(a.row().apply { headers.forEach { h -> addView(a.text(h, 13f, Palette.TEXT_DIM, Palette.display(a)).apply { gravity = Gravity.CENTER }, lp(0, WRAP, 1f)) } }, lp(MATCH, WRAP).margins(b = a.dp(6)))
        var todayCell: View? = null
        days(today).chunked(7).forEach { week ->
            grid.addView(a.row().apply {
                (0 until 7).forEach { i ->
                    val day = week.getOrNull(i)
                    val cell = a.column().apply {
                        gravity = Gravity.CENTER
                        if (day != null) {
                            hudFocus(6f, 1.08f)
                            isClickable = true
                            val events = d.on(day)
                            addView(a.text(day.dayOfMonth.toString(), 18f, when {
                                day == today -> Palette.BG0
                                day.isBefore(today) -> Palette.TEXT_FAINT
                                else -> Palette.TEXT
                            }, Palette.display(a)).apply {
                                gravity = Gravity.CENTER
                                if (day == today) background = ChipDrawable(a.resources.displayMetrics.density, Palette.primary, 1f)
                            }, lp(a.dp(40), a.dp(28)))
                            addView(a.row(Gravity.CENTER).apply {
                                events.distinctBy { it.color }.take(3).forEach { e -> addView(colorDot(a, e.color, 6).apply { (layoutParams as LinearLayout.LayoutParams).setMargins(a.dp(1), 0, a.dp(1), 0) }) }
                            }, lp(MATCH, a.dp(10)).margins(t = a.dp(2)))
                            setOnFocusChangeListener { _, has -> if (has) showDay(day) }
                            if (day == today) todayCell = this
                        }
                    }
                    addView(cell, lp(0, a.dp(48), 1f))
                }
            })
        }
        gridPanel.addView(grid, lp(MATCH, WRAP).margins(t = a.dp(10)))
        addView(gridPanel, lp(0, WRAP, 1.3f).margins(r = a.dp(22)))
        addView(a.panel(focusable = false).apply { addView(side) }, lp(0, MATCH, 1f))
        showDay(today)
        post { todayCell?.requestFocus() }
    }
}
