package com.v2ray.ang.net

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.HttpsURLConnection

object DomainFallback {

    /** لیست دامین‌ها. فقط آدرس پنل خودت را اینجا بگذار */
    private val domains = CopyOnWriteArrayList<String>(
        listOf(
            "https://fachur.ir" 
        )
    )

    fun setDomains(newDomains: List<String>) {
        if (newDomains.isNotEmpty()) {
            domains.clear()
            domains.addAll(newDomains)
        }
    }

    fun addDomains(vararg more: String) {
        more.forEach { d ->
            if (d.isNotBlank() && !domains.contains(d)) domains.add(d)
        }
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return BufferedReader(InputStreamReader(stream)).use { it.readText() }
    }

    fun request(
        path: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        contentType: String? = null
    ): Result<Pair<Int, String>> {
        var lastError: Exception? = null

        for (i in domains.indices) {
            val base = domains[i].trimEnd('/')
            // اصلاح مسیر برای اینکه اگر path خودش اسلش داشت یا نداشت درست کار کنه
            val finalPath = if (path.startsWith("/")) path else "/$path"
            val url = URL("$base$finalPath")
            
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = method
                connectTimeout = 10000 // افزایش تایم‌اوت به ۱۰ ثانیه برای اطمینان
                readTimeout = 10000
                doInput = true
                if (body != null) {
                    doOutput = true
                    contentType?.let { setRequestProperty("Content-Type", it) }
                }
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            try {
                if (body != null) {
                    conn.outputStream.use { it.write(body) }
                }
                val code = conn.responseCode
                val text = readBody(conn)

                if (code in 200..299) {
                    promoteDomainToFront(i)
                    conn.disconnect()
                    return Result.success(code to text)
                } else {
                    // حتی اگر ارور داد هم فعلا ریزالت رو برمیگردونیم تا ApiClient تصمیم بگیره
                    conn.disconnect()
                    return Result.success(code to text)
                }
            } catch (e: Exception) {
                lastError = e
            } finally {
                try { conn.disconnect() } catch (_: Exception) {}
            }
        }

        return Result.failure(lastError ?: RuntimeException("No domain responded"))
    }

    private fun promoteDomainToFront(index: Int) {
        if (index <= 0) return
        val copy = domains[index]
        domains.removeAt(index)
        domains.add(0, copy)
    }
}