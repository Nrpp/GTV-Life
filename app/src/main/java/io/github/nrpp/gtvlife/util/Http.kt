package io.github.nrpp.gtvlife.util

import io.github.nrpp.gtvlife.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object Http {
    private const val USER_AGENT = "GTV-Life/${BuildConfig.VERSION_NAME} (Android TV; https://github.com/${BuildConfig.UPDATE_REPO})"

    /** Abre una conexión siguiendo redirecciones (incluidas http -> https, que Java no sigue sola). */
    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        var current = if (url.startsWith("webcal://", ignoreCase = true)) "https://" + url.substring(9) else url
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location") ?: throw IOException("Redirección sin destino")
                conn.disconnect()
                current = URL(URL(current), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
            return conn
        }
        throw IOException("Demasiadas redirecciones")
    }

    fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = open(url, headers)
        try {
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    fun download(url: String, target: File, onProgress: (Int) -> Unit) {
        val conn = open(url, mapOf("Accept" to "application/octet-stream"))
        try {
            val total = conn.contentLengthLong
            conn.inputStream.use { input: InputStream ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
