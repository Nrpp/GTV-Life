package io.github.nrpp.gtvlife.ui

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nrpp.gtvlife.BuildConfig
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.data.Weather
import io.github.nrpp.gtvlife.data.WeatherCodes
import io.github.nrpp.gtvlife.data.WeatherRepository
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.HudPanelDrawable
import io.github.nrpp.gtvlife.ui.widget.SecondsBarView
import io.github.nrpp.gtvlife.ui.widget.TempChartView
import io.github.nrpp.gtvlife.ui.widget.WeatherIconView
import io.github.nrpp.gtvlife.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : BaseActivity() {

    private lateinit var greeting: TextView
    private lateinit var headerDate: TextView
    private lateinit var headerTime: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView

    private lateinit var cardClock: LinearLayout
    private lateinit var cardCalendar: LinearLayout
    private lateinit var cardWeather: LinearLayout
    private lateinit var settingsButton: TextView

    private lateinit var clockTime: TextView
    private lateinit var clockSeconds: SecondsBarView
    private lateinit var clockDate: TextView

    private lateinit var calCount: TextView
    private lateinit var calHighlight: LinearLayout
    private lateinit var calList: LinearLayout

    private lateinit var wIcon: WeatherIconView
    private lateinit var wTemp: TextView
    private lateinit var wDesc: TextView
    private lateinit var wMinMax: TextView
    private lateinit var wRain: TextView
    private lateinit var wChart: TempChartView

    private lateinit var updateStatus: TextView
    private var online: Boolean? = null

    /** APK ya descargado que espera a que el usuario conceda el permiso de instalación. */
    private var pendingApk: File? = null

    private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Fmt.ES)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = FrameLayout(this)
        val main = buildMain()
        screen.addView(main)
        setScreen(screen)

        val firstRun = !ConfigStore.isConfigured(this)
        if (savedInstanceState == null) {
            if (firstRun) {
                startActivity(Intent(this, SetupActivity::class.java))
            } else {
                playIntro(screen)
            }
            checkForUpdates()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        renderClock()
                        delay(1000L - System.currentTimeMillis() % 1000L)
                    }
                }
                launch {
                    while (true) {
                        refreshData()
                        delay(5 * 60 * 1000L)
                    }
                }
                launch {
                    // Salvapantallas: abre el reloj tras N minutos sin tocar el mando.
                    while (true) {
                        delay(20_000)
                        val mins = config.autoClockMinutes
                        if (mins > 0 && SystemClock.uptimeMillis() - lastInteraction > mins * 60_000L &&
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        ) {
                            startActivity(Intent(this@MainActivity, ClockActivity::class.java))
                        }
                    }
                }
            }
        }
    }

    override fun onConfigLoaded() {
        applyModules()
        lifecycleScope.launch { refreshData() }
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingApk
        if (apk != null && UpdateManager.canInstall(this)) {
            pendingApk = null
            installUpdate(apk)
        }
    }

    // ---- Construcción de la interfaz ----

    private fun buildMain(): View {
        val root = column().apply { setPadding(dp(52), dp(30), dp(52), dp(24)) }

        // Cabecera
        val header = row()
        val left = column()
        greeting = text("", 34f, Palette.TEXT, Palette.semibold(this))
        headerDate = text("", 13f, Palette.TEXT_DIM, Palette.display(this), 0.2f).apply { setPadding(0, dp(6), 0, 0) }
        left.addView(greeting)
        left.addView(headerDate)
        header.addView(left, lp(0, WRAP, 1f))
        val right = column().apply { gravity = Gravity.END }
        headerTime = text("", 30f, Palette.TEXT, Palette.display(this), 0.06f)
        val status = row(Gravity.CENTER_VERTICAL or Gravity.END)
        statusDot = View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Palette.TEXT_FAINT) } }
        statusText = text("CONECTANDO…", 13f, Palette.TEXT_DIM, Palette.semibold(this), 0.12f)
        status.addView(statusDot, lp(dp(8), dp(8)).margins(r = dp(8)))
        status.addView(statusText)
        right.addView(headerTime)
        right.addView(status, lp(WRAP, WRAP).margins(t = dp(6)))
        header.addView(right)
        root.addView(header)

        // Tarjetas
        val cards = row(Gravity.TOP)
        cardClock = card().also { cards.addView(it, lp(0, MATCH, 1f).margins(r = dp(22))) }
        cardCalendar = card().also { cards.addView(it, lp(0, MATCH, 1.25f).margins(r = dp(22))) }
        cardWeather = card().also { cards.addView(it, lp(0, MATCH, 1.05f)) }
        root.addView(cards, lp(MATCH, 0, 1f).margins(t = dp(24)))

        buildClockCard()
        buildCalendarCard()
        buildWeatherCard()

        cardClock.setOnClickListener { open(ClockActivity::class.java) }
        cardCalendar.setOnClickListener { open(CalendarActivity::class.java) }
        cardWeather.setOnClickListener { open(WeatherActivity::class.java) }

        // Pie
        val footer = row()
        settingsButton = chip("⚙  AJUSTES", Palette.primary, 15f).apply {
            typeface = Palette.display(this@MainActivity)
            letterSpacing = 0.15f
            setPadding(dp(22), dp(11), dp(22), dp(11))
            isClickable = true
            hudFocus(8f, 1.06f)
            setOnClickListener { open(SetupActivity::class.java) }
        }
        footer.addView(settingsButton)
        updateStatus = text("", 15f, Palette.TEXT_DIM, Palette.medium(this))
        footer.addView(updateStatus, lp(0, WRAP, 1f).margins(l = dp(22)))
        footer.addView(text("GTV LIFE  v${BuildConfig.VERSION_NAME}", 11f, Palette.TEXT_FAINT, Palette.display(this), 0.2f))
        root.addView(footer, lp(MATCH, WRAP).margins(t = dp(18)))
        return root
    }

    private fun card() = column().apply {
        background = HudPanelDrawable(resources.displayMetrics.density)
        setPadding(dp(24), dp(20), dp(24), dp(18))
        isClickable = true
        hudFocus(18f, 1.03f)
    }

    private fun spacer() = View(this)

    private fun buildClockCard() = with(cardClock) {
        addView(label("Reloj"))
        addView(spacer(), lp(0, 0, 1f))
        clockTime = text("--:--", 60f, Palette.TEXT, Palette.display(this@MainActivity), 0.02f).apply {
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(24, 68, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        addView(clockTime, lp(MATCH, dp(74)))
        clockSeconds = SecondsBarView(this@MainActivity)
        addView(clockSeconds, lp(MATCH, dp(10)).margins(t = dp(10)))
        clockDate = text("", 20f, Palette.TEXT, Palette.semibold(this@MainActivity))
        addView(clockDate, lp(WRAP, WRAP).margins(t = dp(14)))
        addView(spacer(), lp(0, 0, 1f))
        addView(text("OK  ›  AMPLIAR", 10f, Palette.TEXT_FAINT, Palette.display(this@MainActivity), 0.15f))
    }

    private fun buildCalendarCard() = with(cardCalendar) {
        addView(label("Agenda de hoy"))
        calCount = text("…", 24f, Palette.TEXT, Palette.display(this@MainActivity), 0.08f)
        addView(calCount, lp(WRAP, WRAP).margins(t = dp(14)))
        calHighlight = column().apply {
            background = HudPanelDrawable(resources.displayMetrics.density, 10f, strong = true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        addView(calHighlight, lp(MATCH, WRAP).margins(t = dp(12)))
        calList = column()
        addView(calList, lp(MATCH, 0, 1f).margins(t = dp(10)))
    }

    private fun buildWeatherCard() = with(cardWeather) {
        addView(label("Tiempo"))
        val top = row()
        wIcon = WeatherIconView(this@MainActivity)
        top.addView(wIcon, lp(dp(84), dp(84)))
        val col = column()
        wTemp = text("--°", 50f, Palette.TEXT, Palette.display(this@MainActivity))
        wDesc = text("Cargando…", 18f, Palette.TEXT, Palette.semibold(this@MainActivity)).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        col.addView(wTemp)
        col.addView(wDesc, lp(WRAP, WRAP).margins(t = dp(2)))
        top.addView(col, lp(0, WRAP, 1f).margins(l = dp(14)))
        addView(top, lp(MATCH, WRAP).margins(t = dp(10)))
        wMinMax = text("", 16f, Palette.TEXT_DIM, Palette.medium(this@MainActivity))
        addView(wMinMax, lp(WRAP, WRAP).margins(t = dp(6)))
        wRain = text("", 15f)
        addView(wRain, lp(WRAP, WRAP).margins(t = dp(10)))
        wChart = TempChartView(this@MainActivity).apply { compact = true }
        addView(wChart, lp(MATCH, 0, 1f).margins(t = dp(14)))
    }

    private fun open(target: Class<*>) = startActivity(Intent(this, target))

    private fun applyModules() {
        val cards = listOf(cardClock to config.showClock, cardCalendar to config.showCalendar, cardWeather to config.showWeather)
        cards.forEach { (v, show) -> v.visibility = if (show) View.VISIBLE else View.GONE }
        val visible = cards.filter { it.second }.map { it.first }
        visible.forEachIndexed { i, v ->
            (v.layoutParams as LinearLayout.LayoutParams).marginEnd = if (i == visible.lastIndex) 0 else dp(22)
            v.requestLayout()
        }
        val focused = currentFocus
        if (focused == null || focused.visibility != View.VISIBLE) (visible.firstOrNull() ?: settingsButton).requestFocus()
    }

    private fun playIntro(screen: FrameLayout) {
        val overlay = FrameLayout(this).apply { setBackgroundColor(Palette.alpha(Palette.BG0, 0.94f)) }
        val box = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val logo = text("GTV LIFE", 56f, Palette.TEXT, Palette.display(this), 0.9f)
        val bar = View(this).apply { setBackgroundColor(Palette.primary); scaleX = 0f }
        val sub = text("INICIANDO SISTEMAS", 12f, Palette.primary, Palette.display(this), 0.4f).apply { alpha = 0f }
        box.addView(logo)
        box.addView(bar, lp(dp(360), dp(2)).margins(t = dp(14)))
        box.addView(sub, lp(WRAP, WRAP).margins(t = dp(14)))
        overlay.addView(box, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        screen.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        logo.alpha = 0f
        logo.animate().alpha(1f).setDuration(500).start()
        ValueAnimator.ofFloat(0.9f, 0.32f).apply {
            duration = 900
            interpolator = android.view.animation.DecelerateInterpolator(2f)
            addUpdateListener { logo.letterSpacing = it.animatedValue as Float }
            start()
        }
        bar.animate().scaleX(1f).setStartDelay(250).setDuration(800).start()
        sub.animate().alpha(1f).setStartDelay(500).setDuration(400).start()
        overlay.animate().alpha(0f).setStartDelay(1500).setDuration(450).withEndAction {
            screen.removeView(overlay)
        }.start()

        val items = listOf(greeting, headerDate, cardClock, cardCalendar, cardWeather, settingsButton)
        items.forEachIndexed { i, v -> v.enter(1600L + i * 90L, dpf(36f)) }
        greeting.postDelayed({ syncRing() }, 1600L + items.size * 90L + 560L)
    }

    // ---- Datos ----

    private fun renderClock() {
        val now = LocalTime.now()
        val today = LocalDate.now()
        clockTime.text = Fmt.digits(now, config)
        clockSeconds.second = now.second
        clockDate.text = Fmt.shortDate(today)
        headerTime.text = Fmt.digits(now, config)
        greeting.text = Fmt.greeting(now, config.userName)
        headerDate.text = dateFmt.format(today).uppercase(Fmt.ES)
        renderStatus()
    }

    private fun renderStatus() {
        val last = maxOf(WeatherRepository.lastUpdated, CalendarRepository.lastUpdated)
        val (color, msg) = when (online) {
            null -> Palette.TEXT_FAINT to "CONECTANDO…"
            true -> Palette.OK to "EN LÍNEA  ·  ACTUALIZADO ${Fmt.ago(last).uppercase(Fmt.ES)}"
            false -> Palette.DANGER to "SIN CONEXIÓN  ·  ÚLTIMOS DATOS ${Fmt.ago(last).uppercase(Fmt.ES)}"
        }
        (statusDot.background as android.graphics.drawable.GradientDrawable).setColor(color)
        statusText.text = msg
    }

    private suspend fun refreshData() {
        val cfg = config
        var ok: Boolean? = null
        if (cfg.showWeather) {
            if (!cfg.hasLocation) {
                wDesc.text = "Configura tu ubicación"
            } else {
                runCatching { WeatherRepository.get(cfg) }
                    .onSuccess { renderWeather(it); ok = true }
                    .onFailure {
                        ok = false
                        if (WeatherRepository.peek() == null) wDesc.text = "Sin conexión"
                    }
            }
        }
        if (cfg.showCalendar) {
            if (cfg.calendars.isEmpty()) {
                calCount.text = "SIN CALENDARIOS"
                calHighlight.removeAllViews()
                calHighlight.addView(text("Añádelos desde Ajustes escaneando el QR", 17f, Palette.TEXT_DIM))
                calList.removeAllViews()
            } else {
                runCatching { CalendarRepository.get(cfg) }
                    .onSuccess { renderCalendar(it); if (ok != false) ok = it.errors.size < cfg.calendars.size }
                    .onFailure { ok = false }
            }
        }
        if (ok != null) online = ok
        renderStatus()
    }

    private fun renderWeather(w: Weather) {
        wIcon.set(w.code, w.isDay)
        wTemp.text = Fmt.temp(w.temp)
        wDesc.text = WeatherCodes.describe(w.code)
        val place = w.locationName.substringBefore(",").ifBlank { null }
        wMinMax.text = listOfNotNull(place, w.today?.let { "↑${Fmt.temp(it.max)}  ↓${Fmt.temp(it.min)}" }).joinToString("   ·   ")
        val rain = w.willRainToday
        wRain.text = if (rain) "☂  LLEVA PARAGUAS" else "✓  SIN LLUVIA HOY"
        wRain.typeface = Palette.display(this)
        wRain.textSize = 12f
        wRain.letterSpacing = 0.12f
        wRain.background = io.github.nrpp.gtvlife.ui.widget.ChipDrawable(resources.displayMetrics.density, if (rain) Palette.WARN else Palette.OK)
        wRain.setPadding(dp(12), dp(6), dp(12), dp(6))
        wChart.hours = w.next(24)
    }

    private fun renderCalendar(r: CalendarResult) {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val events = r.on(today)
        calCount.text = when (events.size) {
            0 -> "DÍA LIBRE"
            1 -> "1 EVENTO"
            else -> "${events.size} EVENTOS"
        }

        calHighlight.removeAllViews()
        val current = r.current(now)
        val next = r.next(now)?.takeIf { it.start.atZone(zone).toLocalDate() == today }
        val focus = current ?: next
        if (focus != null) {
            val tag = if (focus === current) "EN CURSO  ·  ${Fmt.remaining(focus.end, now).uppercase(Fmt.ES)}"
            else "SIGUIENTE  ·  ${Fmt.until(focus.start, now).uppercase(Fmt.ES)}"
            calHighlight.addView(text(tag, 11f, Palette.primary, Palette.display(this), 0.12f))
            calHighlight.addView(text(focus.title, 22f, Palette.TEXT, Palette.bold(this)).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(6), 0, dp(2))
            })
            calHighlight.addView(text(Fmt.eventTime(focus, today, config) + (focus.location?.let { "  ·  $it" } ?: ""), 16f, Palette.TEXT_DIM).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
        } else {
            calHighlight.addView(text(if (events.isEmpty()) "Nada en la agenda. ¡Disfruta!" else "No quedan más eventos hoy", 18f, Palette.TEXT_DIM, Palette.semibold(this)))
        }

        calList.removeAllViews()
        val rest = events.filter { it !== focus && (it.allDay || it.end > now) }
        rest.take(3).forEach { e ->
            val row = row().apply { setPadding(0, dp(5), 0, dp(5)) }
            row.addView(colorDot(this, e.color, 10))
            row.addView(text(Fmt.eventTime(e, today, config), 15f, Palette.TEXT_DIM, Palette.semibold(this)).apply {
                setPadding(dp(10), 0, dp(10), 0)
            })
            row.addView(text(e.title, 17f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            calList.addView(row)
        }
        if (rest.size > 3) calList.addView(text("+${rest.size - 3} MÁS", 11f, Palette.TEXT_FAINT, Palette.display(this), 0.15f).apply { setPadding(0, dp(4), 0, 0) })
        if (r.errors.isNotEmpty()) {
            calList.addView(text("⚠  ${r.errors.size} calendario(s) con error", 14f, Palette.WARN).apply { setPadding(0, dp(6), 0, 0) })
        }
    }

    // ---- Actualizaciones automáticas desde GitHub Releases ----

    private fun checkForUpdates() {
        // Las compilaciones locales (versión 0.0.0) no se autoactualizan.
        if (BuildConfig.VERSION_NAME == "0.0.0") return
        lifecycleScope.launch {
            updateStatus.text = "Buscando actualizaciones…"
            val release = runCatching { UpdateManager.checkLatest() }.getOrElse {
                updateStatus.text = ""
                return@launch
            }
            if (release == null) {
                updateStatus.text = "Tienes la última versión"
                delay(4000)
                updateStatus.text = ""
                return@launch
            }
            updateStatus.text = "Descargando la versión ${release.version}…"
            val apk = runCatching {
                UpdateManager.download(this@MainActivity, release) { pct ->
                    runOnUiThread { updateStatus.text = "Descargando la versión ${release.version}… $pct%" }
                }
            }.getOrElse {
                updateStatus.text = "No se pudo descargar la actualización"
                return@launch
            }
            if (UpdateManager.canInstall(this@MainActivity)) installUpdate(apk) else askInstallPermission(apk)
        }
    }

    private fun installUpdate(apk: File) {
        lifecycleScope.launch {
            updateStatus.text = "Instalando actualización…"
            runCatching { UpdateManager.install(this@MainActivity, apk) }
                .onFailure { updateStatus.text = "Error al instalar: ${it.message}" }
        }
    }

    private fun askInstallPermission(apk: File) {
        pendingApk = apk
        updateStatus.text = "Actualización lista: falta el permiso para instalar"
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Actualización disponible")
            .setMessage(
                "Para instalar actualizaciones automáticamente, permite que GTV Life instale aplicaciones " +
                    "(«Instalar apps desconocidas»). Solo hay que hacerlo una vez.",
            )
            .setPositiveButton("Abrir ajustes") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                try {
                    startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        this,
                        "Ve a Ajustes › Apps › Seguridad › Fuentes desconocidas y activa GTV Life",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }
}
