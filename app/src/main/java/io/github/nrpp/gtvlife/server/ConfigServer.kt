package io.github.nrpp.gtvlife.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import io.github.nrpp.gtvlife.BuildConfig
import io.github.nrpp.gtvlife.R
import io.github.nrpp.gtvlife.data.AppConfig
import io.github.nrpp.gtvlife.data.CalendarRepository
import io.github.nrpp.gtvlife.data.ConfigStore
import io.github.nrpp.gtvlife.data.WeatherRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Servidor web local que sirve la página de configuración que se abre en el móvil al escanear el QR.
 * Todas las llamadas a la API exigen el token aleatorio incluido en el QR, de modo que solo quien
 * ve la pantalla de la TV puede cambiar la configuración.
 */
class ConfigServer(
    private val context: Context,
    port: Int,
    private val onSaved: (AppConfig) -> Unit,
) : NanoHTTPD(port) {

    val token: String = ByteArray(12).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    override fun serve(session: IHTTPSession): Response {
        return try {
            route(session)
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "Error"))
        }
    }

    private fun route(session: IHTTPSession): Response {
        val path = session.uri
        if (path == "/" || path == "/index.html") {
            val html = context.assets.open("config.html").use { it.readBytes().toString(Charsets.UTF_8) }
            return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html).noCache()
        }
        FONTS[path]?.let { res ->
            val bytes = context.resources.openRawResource(res).use { it.readBytes() }
            return newFixedLengthResponse(Response.Status.OK, "font/ttf", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
                .apply { addHeader("Cache-Control", "max-age=86400") }
        }
        if (!path.startsWith("/api/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No encontrado")
        }
        if (!authorized(session)) {
            return json(Response.Status.FORBIDDEN, JSONObject().put("error", "QR caducado. Vuelve a escanear el código de la TV."))
        }
        return when {
            path == "/api/config" && session.method == Method.GET ->
                json(Response.Status.OK, ConfigStore.load(context).toJson().put("appVersion", BuildConfig.VERSION_NAME))

            path == "/api/config" && session.method == Method.POST -> {
                val cfg = AppConfig.fromJson(JSONObject(readBody(session)))
                ConfigStore.save(context, cfg)
                onSaved(cfg)
                json(Response.Status.OK, JSONObject().put("ok", true))
            }

            path == "/api/geocode" -> {
                val q = session.parameters["q"]?.firstOrNull()?.trim().orEmpty()
                if (q.length < 2) return json(Response.Status.OK, JSONObject().put("results", JSONArray()))
                val res = WeatherRepository.geocode(q)
                json(Response.Status.OK, JSONObject().put("results", res.optJSONArray("results") ?: JSONArray()))
            }

            path == "/api/test-calendar" && session.method == Method.POST -> {
                val url = JSONObject(readBody(session)).optString("url").trim()
                if (!AppConfig.isValidCalendarUrl(url)) {
                    return json(Response.Status.OK, JSONObject().put("ok", false).put("error", "La dirección debe empezar por https://, http:// o webcal://"))
                }
                val result = runCatching { runBlocking { CalendarRepository.test(url) } }
                json(
                    Response.Status.OK,
                    result.fold(
                        { JSONObject().put("ok", true).put("events", it) },
                        { JSONObject().put("ok", false).put("error", it.message ?: it.javaClass.simpleName) },
                    ),
                )
            }

            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No encontrado")
        }
    }

    private fun authorized(session: IHTTPSession): Boolean {
        val given = session.headers["x-token"] ?: session.parameters["t"]?.firstOrNull() ?: return false
        return MessageDigest.isEqual(given.toByteArray(), token.toByteArray())
    }

    private fun readBody(session: IHTTPSession): String {
        val len = session.headers["content-length"]?.toIntOrNull() ?: 0
        require(len in 0..256 * 1024) { "Petición demasiado grande" }
        val buf = ByteArray(len)
        var read = 0
        while (read < len) {
            val n = session.inputStream.read(buf, read, len - read)
            if (n < 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).noCache()

    private fun Response.noCache() = apply { addHeader("Cache-Control", "no-store") }

    companion object {
        /** Las mismas fuentes que usa la TV, para que la web del móvil tenga el mismo aspecto. */
        private val FONTS = mapOf(
            "/fonts/orbitron.ttf" to R.font.orbitron,
            "/fonts/rajdhani-regular.ttf" to R.font.rajdhani_regular,
            "/fonts/rajdhani-semibold.ttf" to R.font.rajdhani_semibold,
            "/fonts/rajdhani-bold.ttf" to R.font.rajdhani_bold,
        )

        /** Arranca el servidor en el primer puerto libre a partir de 8080. */
        fun start(context: Context, onSaved: (AppConfig) -> Unit): ConfigServer? {
            for (port in 8080..8090) {
                val server = ConfigServer(context.applicationContext, port, onSaved)
                try {
                    server.start(SOCKET_READ_TIMEOUT, true)
                    return server
                } catch (_: Exception) {
                    server.stop()
                }
            }
            return null
        }
    }
}
