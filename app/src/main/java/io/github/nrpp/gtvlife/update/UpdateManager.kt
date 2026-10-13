package io.github.nrpp.gtvlife.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import io.github.nrpp.gtvlife.BuildConfig
import io.github.nrpp.gtvlife.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Busca el último lanzamiento en GitHub Releases y, si su versión es mayor que la instalada,
 * descarga el APK adjunto y lo instala con PackageInstaller.
 */
object UpdateManager {

    data class Release(val version: String, val tag: String, val apkUrl: String, val apkName: String)

    suspend fun checkLatest(): Release? = withContext(Dispatchers.IO) {
        val json = JSONObject(
            Http.getString(
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
                mapOf("Accept" to "application/vnd.github+json"),
            ),
        )
        val tag = json.optString("tag_name")
        val assets = json.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null
        val release = Release(
            version = tag.removePrefix("v").removePrefix("V"),
            tag = tag,
            apkUrl = apk.getString("browser_download_url"),
            apkName = apk.getString("name"),
        )
        if (isNewer(release.version, BuildConfig.VERSION_NAME)) release else null
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote) ?: return false
        val l = parts(local) ?: return true
        for (i in 0 until 3) {
            if (r[i] != l[i]) return r[i] > l[i]
        }
        return false
    }

    private fun parts(v: String): List<Int>? =
        Regex("""(\d+)\.(\d+)\.(\d+)""").find(v)?.groupValues?.drop(1)?.map { it.toInt() }

    suspend fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "update-${release.version}.apk")
            Http.download(release.apkUrl, target, onProgress)
            target
        }

    /** Hace falta el permiso de "instalar apps desconocidas" para esta app. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                // Si esta app es quien instaló la versión actual, Android permite actualizar sin preguntar.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }
}
