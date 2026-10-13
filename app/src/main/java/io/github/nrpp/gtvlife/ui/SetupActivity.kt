package io.github.nrpp.gtvlife.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.BuildConfig
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.server.ConfigServer
import io.github.nrpp.gtvlife.ui.theme.Palette
import io.github.nrpp.gtvlife.ui.widget.ChipDrawable
import io.github.nrpp.gtvlife.ui.widget.QrPanelView
import io.github.nrpp.gtvlife.update.UpdateFlow
import io.github.nrpp.gtvlife.util.Net
import io.github.nrpp.gtvlife.util.Qr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Muestra un QR que abre en el móvil la página de configuración servida por la propia TV.
 * El servidor solo está activo mientras esta pantalla está visible.
 */
class SetupActivity : BaseActivity() {

    private var server: ConfigServer? = null
    private lateinit var qr: QrPanelView
    private lateinit var url: TextView
    private lateinit var status: TextView
    private lateinit var summary: LinearLayout
    private lateinit var updateButton: TextView
    private lateinit var back: TextView

    override fun preferredFocus(): View = back
    private lateinit var updateStatus: TextView

    private val updates = UpdateFlow(this) { state, msg ->
        updateStatus.text = msg
        updateStatus.setTextColor(
            when (state) {
                UpdateFlow.State.BUSY -> Palette.primary
                UpdateFlow.State.UP_TO_DATE -> Palette.OK
                UpdateFlow.State.ERROR, UpdateFlow.State.READY -> Palette.WARN
            },
        )
        updateButton.alpha = if (state == UpdateFlow.State.BUSY) 0.5f else 1f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = row().apply { setPadding(dp(64), dp(30), dp(64), dp(30)) }
        val left = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        qr = QrPanelView(this)
        left.addView(qr, lp(dp(340), dp(340)))
        updateButton = chip("⟳  BUSCAR ACTUALIZACIONES", Palette.primary, 13f).apply {
            typeface = Palette.display(this@SetupActivity)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            isClickable = true
            hudFocus(8f, 1.04f)
            setOnClickListener { updates.check(manual = true) }
        }
        left.addView(updateButton, lp(dp(310), WRAP).margins(t = dp(12)))
        updateStatus = text("Versión instalada: v${BuildConfig.VERSION_NAME}", 15f, Palette.TEXT_DIM, Palette.semibold(this)).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }
        left.addView(updateStatus, lp(dp(330), WRAP).margins(t = dp(10)))
        root.addView(left)

        val col = column()
        col.addView(label("Configuración"))
        col.addView(text("Configúrala desde el móvil", 34f, Palette.TEXT, Palette.bold(this)).apply { setPadding(0, dp(10), 0, dp(4)) })
        listOf(
            "Conecta el móvil al mismo Wi-Fi que la TV",
            "Escanea el código con la cámara",
            "Elige ubicación, calendarios, colores y reloj",
            "Pulsa «Guardar» y la TV se actualiza al instante",
        ).forEachIndexed { i, s ->
            val r = row().apply { setPadding(0, dp(10), 0, 0) }
            r.addView(text("0${i + 1}", 14f, Palette.primary, Palette.display(this)).apply {
                background = ChipDrawable(resources.displayMetrics.density, Palette.primary, 0.1f)
                gravity = Gravity.CENTER
            }, lp(dp(44), dp(30)))
            r.addView(text(s, 19f, Palette.TEXT, Palette.medium(this)), lp(WRAP, WRAP).margins(l = dp(14)))
            col.addView(r)
        }
        url = text("", 14f, Palette.TEXT_DIM, Palette.medium(this)).apply { setPadding(0, dp(14), 0, 0) }
        col.addView(url)
        summary = row()
        col.addView(summary, lp(WRAP, WRAP).margins(t = dp(16)))
        status = text("", 19f, Palette.OK, Palette.bold(this)).apply { setPadding(0, dp(12), 0, 0) }
        col.addView(status)
        back = chip("‹  VOLVER AL MENÚ", Palette.primary, 14f).apply {
            typeface = Palette.display(this@SetupActivity)
            letterSpacing = 0.15f
            setPadding(dp(22), dp(11), dp(22), dp(11))
            isClickable = true
            hudFocus(8f, 1.06f)
            setOnClickListener { finish() }
        }
        col.addView(back, lp(WRAP, WRAP).margins(t = dp(14)))
        root.addView(col, lp(0, WRAP, 1f).margins(l = dp(56)))
        setScreen(root)
        back.requestFocus()
    }

    override fun onConfigLoaded() {
        summary.removeAllViews()
        fun add(s: String) = summary.addView(chip(s, Palette.secondary, 14f), lp(WRAP, WRAP).margins(r = dp(10)))
        add(if (config.hasLocation) "📍 " + config.locationName.substringBefore(",") else "📍 Sin ubicación")
        add("📅 " + when (config.calendars.size) { 0 -> "Sin calendarios"; 1 -> "1 calendario"; else -> "${config.calendars.size} calendarios" })
    }

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }

    override fun onStart() {
        super.onStart()
        val ip = Net.localIpv4()
        if (ip == null) {
            status.setTextColor(Palette.WARN)
            status.text = "La TV no está conectada a ninguna red. Conéctala a tu Wi-Fi y vuelve a abrir esta pantalla."
            return
        }
        val srv = ConfigServer.start(this, ::onSaved)
        if (srv == null) {
            status.setTextColor(Palette.WARN)
            status.text = "No se pudo iniciar el servidor de configuración."
            return
        }
        server = srv
        val link = "http://$ip:${srv.listeningPort}/?t=${srv.token}"
        qr.bitmap = Qr.bitmap(link, 480)
        url.text = "o abre en el navegador del móvil:  $link"
    }

    override fun onStop() {
        super.onStop()
        server?.stop()
        server = null
    }

    private fun onSaved(@Suppress("UNUSED_PARAMETER") config: AppConfig) {
        lifecycleScope.launch {
            status.setTextColor(Palette.OK)
            status.text = "✓  Configuración guardada. Volviendo al menú…"
            delay(2200)
            finish()
        }
    }
}
