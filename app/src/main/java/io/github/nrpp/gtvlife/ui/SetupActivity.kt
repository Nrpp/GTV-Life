package io.github.nrpp.gtvlife.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.server.ConfigServer
import io.github.nrpp.gtvlife.util.Net
import io.github.nrpp.gtvlife.util.Qr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Muestra un QR que abre en el móvil la página de configuración servida por la propia TV.
 * El servidor solo está activo mientras esta pantalla está visible.
 */
class SetupActivity : ComponentActivity() {

    private var server: ConfigServer? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        status = findViewById(R.id.status)
        findViewById<TextView>(R.id.btnBack).apply {
            setOnClickListener { finish() }
            focusZoom(1.06f)
            requestFocus()
        }
    }

    override fun onStart() {
        super.onStart()
        val ip = Net.localIpv4()
        if (ip == null) {
            status.text = "La TV no está conectada a ninguna red. Conéctala a tu Wi-Fi y vuelve a abrir esta pantalla."
            return
        }
        val srv = ConfigServer.start(this, ::onSaved)
        if (srv == null) {
            status.text = "No se pudo iniciar el servidor de configuración."
            return
        }
        server = srv
        val url = "http://$ip:${srv.listeningPort}/?t=${srv.token}"
        findViewById<ImageView>(R.id.qr).setImageBitmap(Qr.bitmap(url, 640))
        findViewById<TextView>(R.id.url).text = "o abre en el navegador del móvil:\nhttp://$ip:${srv.listeningPort}/?t=${srv.token}"
        status.text = ""
    }

    override fun onStop() {
        super.onStop()
        server?.stop()
        server = null
    }

    private fun onSaved(@Suppress("UNUSED_PARAMETER") config: AppConfig) {
        lifecycleScope.launch {
            status.text = "✓ Configuración guardada. Volviendo al menú…"
            delay(2500)
            finish()
        }
    }
}
