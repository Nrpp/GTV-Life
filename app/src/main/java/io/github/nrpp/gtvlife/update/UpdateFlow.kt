package io.github.nrpp.gtvlife.update

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.nrpp.gtvlife.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Flujo completo de actualización para una pantalla: buscar en GitHub, descargar, pedir el permiso
 * de instalación si falta e instalar. Informa de cada paso con [onStatus].
 */
class UpdateFlow(
    private val activity: ComponentActivity,
    private val onStatus: (State, String) -> Unit,
) {
    enum class State { BUSY, UP_TO_DATE, ERROR, READY }

    /** APK ya descargado que espera a que el usuario conceda el permiso de instalación. */
    private var pendingApk: File? = null
    private var job: Job? = null

    val isRunning get() = job?.isActive == true

    /** [manual] = lo ha pedido el usuario con el botón (se informa también de "estás al día"). */
    fun check(manual: Boolean) {
        if (isRunning) return
        // Las compilaciones locales (versión 0.0.0) no se autoactualizan.
        if (BuildConfig.VERSION_NAME == "0.0.0") {
            if (manual) onStatus(State.ERROR, "Versión de desarrollo: no se actualiza desde GitHub")
            return
        }
        job = activity.lifecycleScope.launch {
            onStatus(State.BUSY, "Buscando actualizaciones…")
            val release = runCatching { UpdateManager.checkLatest() }.getOrElse {
                onStatus(State.ERROR, if (manual) "No se pudo comprobar: ${it.message ?: "sin conexión"}" else "")
                return@launch
            }
            if (release == null) {
                onStatus(State.UP_TO_DATE, "Tienes la última versión (v${BuildConfig.VERSION_NAME})")
                return@launch
            }
            onStatus(State.BUSY, "Descargando la versión ${release.version}…")
            val apk = runCatching {
                UpdateManager.download(activity, release) { pct ->
                    activity.runOnUiThread { onStatus(State.BUSY, "Descargando la versión ${release.version}… $pct%") }
                }
            }.getOrElse {
                onStatus(State.ERROR, "No se pudo descargar la actualización")
                return@launch
            }
            if (UpdateManager.canInstall(activity)) install(apk) else askPermission(apk)
        }
    }

    /** Llamar desde onResume: si se acaba de conceder el permiso, instala lo pendiente. */
    fun onResume() {
        val apk = pendingApk ?: return
        if (UpdateManager.canInstall(activity)) {
            pendingApk = null
            install(apk)
        }
    }

    private fun install(apk: File) {
        activity.lifecycleScope.launch {
            onStatus(State.BUSY, "Instalando actualización…")
            runCatching { UpdateManager.install(activity, apk) }
                .onFailure { onStatus(State.ERROR, "Error al instalar: ${it.message}") }
        }
    }

    private fun askPermission(apk: File) {
        pendingApk = apk
        onStatus(State.READY, "Actualización lista: falta el permiso para instalar")
        AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Actualización disponible")
            .setMessage(
                "Para instalar actualizaciones automáticamente, permite que GTV Life instale aplicaciones " +
                    "(«Instalar apps desconocidas»). Solo hay que hacerlo una vez.",
            )
            .setPositiveButton("Abrir ajustes") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                try {
                    activity.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        activity,
                        "Ve a Ajustes › Apps › Seguridad › Fuentes desconocidas y activa GTV Life",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }
}
