package com.v2ray.ang.net

import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class StatusResponse(
    val username: String? = null,
    val used_traffic: Long? = null,
    val data_limit: Long? = null,
    val expire: Long? = null,
    val status: String? = null,
    val links: List<String>? = null,
    val need_to_update: Boolean? = null,
    val is_ignoreable: Boolean? = null
)

object ApiClient {

    // -------------------------
    // Helpers
    // -------------------------

    private fun jsonHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
    }

    private fun bytes(body: JSONObject): ByteArray =
        body.toString().toByteArray(StandardCharsets.UTF_8)

    // -------------------------
    // Endpoints
    // -------------------------

    fun postLogin(
        username: String,
        password: String,
        deviceId: String,
        appVersion: String,
        cb: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                // بدنه درخواست دقیقا مثل چیزی که پنل شما انتظار دارد
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                // درخواست به فایل panel.php با پارامتر api=login
                val res = DomainFallback.request(
                    path = "/panel.php?api=login",
                    method = "POST",
                    headers = jsonHeaders(),
                    body = bytes(body),
                    contentType = "application/json"
                )
                res.fold(
                    onSuccess = { (code, text) ->
                        if (code in 200..299) {
                            val j = JSONObject(text)
                            if (j.optBoolean("success")) {
                                // ترفند: چون پنل توکن نمی‌دهد، ما یوزر/پسورد را به شکل توکن ذخیره می‌کنیم
                                // تا در مرحله بعد برای گرفتن آپدیت استفاده کنیم.
                                val fakeToken = "$username:$password"
                                cb(Result.success(fakeToken))
                            } else {
                                cb(Result.failure(Exception(j.optString("message", "Login failed"))))
                            }
                        } else {
                            cb(Result.failure(Exception("HTTP Error: $code")))
                        }
                    },
                    onFailure = { cb(Result.failure(it)) }
                )
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }

    fun getStatus(
        token: String,
        cb: (Result<StatusResponse>) -> Unit
    ) {
        Thread {
            try {
                // بازیابی یوزر/پسورد از توکن جعلی که در مرحله قبل ساختیم
                val parts = token.split(":")
                if (parts.size < 2) {
                    cb(Result.failure(Exception("Invalid credentials")))
                    return@Thread
                }
                val username = parts[0]
                val password = parts[1]

                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                // دوباره درخواست لاگین می‌زنیم چون پنل شما اطلاعات ترافیک را در پاسخ لاگین می‌دهد
                val res = DomainFallback.request(
                    path = "/panel.php?api=login",
                    method = "POST",
                    headers = jsonHeaders(),
                    body = bytes(body),
                    contentType = "application/json"
                )
                res.fold(
                    onSuccess = { (code, text) ->
                        if (code in 200..299) {
                            val j = JSONObject(text)
                            if (j.optBoolean("success")) {
                                val data = j.getJSONObject("data")
                                
                                // استخراج اطلاعات ترافیک از پاسخ پنل شما
                                val traffic = data.optJSONObject("traffic")
                                val total = traffic?.optLong("total") ?: 0L
                                val used = traffic?.optLong("used") ?: 0L
                                val expire = traffic?.optLong("expire_ts") ?: 0L
                                
                                // لینک اشتراک (سابسکریپشن)
                                val subUrl = data.optString("subscription_url")
                                val linksList = if (subUrl.isNotEmpty()) listOf(subUrl) else emptyList()

                                // تنظیمات آپدیت اپلیکیشن
                                val appConfig = data.optJSONObject("app_config")
                                val needUpdate = appConfig?.optString("version", "1.0.0") != "1.0.0" // منطق ساده آپدیت

                                val resp = StatusResponse(
                                    username = data.optString("username"),
                                    used_traffic = used,
                                    data_limit = total,
                                    expire = expire,
                                    status = "active",
                                    links = linksList,
                                    need_to_update = false, // فعلا غیرفعال
                                    is_ignoreable = true
                                )
                                cb(Result.success(resp))
                            } else {
                                cb(Result.failure(Exception("Auth Failed")))
                            }
                        } else {
                            cb(Result.failure(Exception("HTTP $code")))
                        }
                    },
                    onFailure = { cb(Result.failure(it)) }
                )
            } catch (e: Exception) {
                cb(Result.failure(e))
            }
        }.start()
    }

    // سایر متدها را می‌توانیم خالی بگذاریم یا پیاده‌سازی نکنیم چون فعلا نیازی نیست
    fun postKeepAlive(token: String, cb: (Result<Unit>) -> Unit) {
        cb(Result.success(Unit)) 
    }

    fun postUpdateFcmToken(token: String, fcmToken: String, cb: (Result<Unit>) -> Unit) {
        // اگر پنل شما متد آپدیت توکن ندارد، این را نادیده می‌گیریم
        cb(Result.success(Unit))
    }

    fun postLogout(token: String, cb: (Result<Unit>) -> Unit) {
        cb(Result.success(Unit))
    }
    
    fun postUpdatePromptSeen(token: String, cb: (Result<Unit>) -> Unit) {
        cb(Result.success(Unit))
    }

    fun postReportUpdate(token: String, version: String, cb: (Result<Unit>) -> Unit) = 
        postReportUpdate(token, "android", version, cb)

    fun postReportUpdate(token: String, platform: String, version: String, cb: (Result<Unit>) -> Unit) {
        cb(Result.success(Unit))
    }
}