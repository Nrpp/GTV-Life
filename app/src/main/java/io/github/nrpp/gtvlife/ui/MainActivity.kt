package io.github.nrpp.gtvlife.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.nrpp.gtvlife.BuildConfig
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.CalendarResult
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.data.Weather
import io.github.nrpp.gtvlife.data.WeatherCodes
import io.github.nrpp.gtvlife.data.WeatherRepository
import io.github.nrpp.gtvlife.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {

    private lateinit var config: AppConfig

    private lateinit var headerDate: TextView
    private lateinit var clockTime: TextView
    private lateinit var clockDate: TextView
    private lateinit var calSummary: TextView
    private lateinit var calList: LinearLayout
    private lateinit var weatherEmoji: TextView
    private lateinit var weatherTemp: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var weatherMinMax: TextView
    private lateinit var weatherRain: TextView
    private lateinit var updateStatus: TextView

    /** APK ya descargado que espera a que el usuario conceda el permiso de instalación. */
    private var pendingApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        headerDate = findViewById(R.id.headerDate)
        clockTime = findViewById(R.id.clockTime)
        clockDate = findViewById(R.id.clockDate)
        calSummary = findViewById(R.id.calSummary)
        calList = findViewById(R.id.calList)
        weatherEmoji = findViewById(R.id.weatherEmoji)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherDesc = findViewById(R.id.weatherDesc)
        weatherMinMax = findViewById(R.id.weatherMinMax)
        weatherRain = findViewById(R.id.weatherRain)
        updateStatus = findViewById(R.id.updateStatus)
        findViewById<TextView>(R.id.versionText).text = "v${BuildConfig.VERSION_NAME}"

        bind(R.id.cardClock, ClockActivity::class.java)
        bind(R.id.cardCalendar, CalendarActivity::class.java)
        bind(R.id.cardWeather, WeatherActivity::class.java)
        bind(R.id.btnSettings, SetupActivity::class.java)

        config = ConfigStore.load(this)
        if (savedInstanceState == null) {
            if (!ConfigStore.isConfigured(this)) startActivity(Intent(this, SetupActivity::class.java))
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
            }
        }
    }

    override fun onStart() {
        super.onStart()
        config = ConfigStore.load(this)
        applyModules()
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingApk
        if (apk != null && UpdateManager.canInstall(this)) {
            pendingApk = null
            installUpdate(apk)
        }
    }

    private fun bind(id: Int, target: Class<*>) {
        findViewById<View>(id).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, target)) }
            focusZoom(if (id == R.id.btnSettings) 1.06f else 1.03f)
        }
    }

    private fun applyModules() {
        val cards = listOf(
            findViewById<View>(R.id.cardClock) to config.showClock,
            findViewById<View>(R.id.cardCalendar) to config.showCalendar,
            findViewById<View>(R.id.cardWeather) to config.showWeather,
        )
        cards.forEach { (v, show) -> v.visibility = if (show) View.VISIBLE else View.GONE }
        // La última tarjeta visible no necesita margen a la derecha.
        val visible = cards.filter { it.second }.map { it.first }
        visible.forEachIndexed { i, v ->
            (v.layoutParams as LinearLayout.LayoutParams).marginEnd = if (i == visible.lastIndex) 0 else dp(24)
            v.requestLayout()
        }
        val first = visible.firstOrNull() ?: findViewById(R.id.btnSettings)
        if (currentFocus == null || currentFocus?.visibility != View.VISIBLE) first.requestFocus()
    }

    private fun renderClock() {
        val now = LocalTime.now()
        val today = LocalDate.now()
        clockTime.text = Fmt.time(now, config)
        clockDate.text = Fmt.longDate(today)
        headerDate.text = "${Fmt.longDate(today)} · ${Fmt.hour(now)}"
    }

    private suspend fun refreshData() {
        val cfg = config
        if (cfg.showWeather) {
            if (!cfg.hasLocation) {
                weatherDesc.text = "Configura tu ubicación"
            } else {
                runCatching { WeatherRepository.get(cfg) }
                    .onSuccess { renderWeather(it) }
                    .onFailure { if (WeatherRepository.peek() == null) weatherDesc.text = "Sin conexión con el servicio del tiempo" }
            }
        }
        if (cfg.showCalendar) {
            if (cfg.calendars.isEmpty()) {
                calSummary.text = "Añade tus calendarios desde Configuración"
                calList.removeAllViews()
            } else {
                runCatching { CalendarRepository.get(cfg) }.onSuccess { renderCalendar(it) }
            }
        }
    }

    private fun renderWeather(w: Weather) {
        weatherEmoji.text = WeatherCodes.emoji(w.code, w.isDay)
        weatherTemp.text = Fmt.temp(w.temp)
        weatherDesc.text = WeatherCodes.describe(w.code) + if (w.locationName.isNotBlank()) " · ${w.locationName.substringBefore(",")}" else ""
        w.today?.let { weatherMinMax.text = "Máx. ${Fmt.temp(it.max)}  ·  Mín. ${Fmt.temp(it.min)}" }
        weatherRain.text = (if (w.willRainToday) "☔ " else "🌂 ") + w.rainSummary()
        weatherRain.setTextColor(getColor(if (w.willRainToday) R.color.warn else R.color.text))
    }

    private fun renderCalendar(r: CalendarResult) {
        val today = LocalDate.now()
        val events = r.on(today)
        calSummary.text = when (events.size) {
            0 -> "No tienes eventos hoy 🎉"
            1 -> "1 evento hoy"
            else -> "${events.size} eventos hoy"
        }
        calList.removeAllViews()
        val max = 5
        events.take(max).forEach { e ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(colorDot(this, e.color, 12))
            row.addView(text(Fmt.eventTime(e, today, config), 17f, getColor(R.color.text_dim)).apply {
                setPadding(dp(12), 0, dp(12), 0)
            })
            row.addView(text(e.title, 19f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            calList.addView(row)
        }
        if (events.size > max) calList.addView(text("y ${events.size - max} más…", 17f, getColor(R.color.text_dim)))
        if (r.errors.isNotEmpty()) {
            calList.addView(text("⚠ ${r.errors.size} calendario(s) con error", 16f, getColor(R.color.warn)).apply {
                setPadding(0, dp(8), 0, 0)
            })
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
