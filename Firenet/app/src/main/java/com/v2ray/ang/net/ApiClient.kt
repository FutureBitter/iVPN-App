package com.v2ray.ang.net

import org.json.JSONObject
import java.nio.charset.StandardCharsets

// مدل داده‌ای که اپلیکیشن انتظار دارد دریافت کند
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

    private fun jsonHeaders(): Map<String, String> {
        return mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json"
        )
    }

    private fun bytes(body: JSONObject): ByteArray =
        body.toString().toByteArray(StandardCharsets.UTF_8)

    /**
     * متد لاگین:
     * درخواست را به panel.php?api=login می‌فرستد.
     * اگر موفق بود، یوزرنیم و پسورد را ترکیب می‌کند و به عنوان "توکن" برمی‌گرداند.
     */
    fun postLogin(
        username: String,
        password: String,
        deviceId: String,
        appVersion: String,
        cb: (Result<String>) -> Unit
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                // ارسال درخواست به پنل شما
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
                            try {
                                val j = JSONObject(text)
                                if (j.optBoolean("success")) {
                                    // ترفند: ذخیره یوزر و پسورد به جای توکن واقعی
                                    val fakeToken = "$username:$password"
                                    cb(Result.success(fakeToken))
                                } else {
                                    val msg = j.optString("message", "نام کاربری یا رمز عبور اشتباه است")
                                    cb(Result.failure(Exception(msg)))
                                }
                            } catch (e: Exception) {
                                cb(Result.failure(Exception("Json Error: $text")))
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

    /**
     * دریافت وضعیت (Status):
     * چون پنل شما متد جداگانه‌ای برای status ندارد و همه چیز را در لاگین می‌دهد،
     * ما اینجا دوباره درخواست لاگین می‌فرستیم و اطلاعات ترافیک را استخراج می‌کنیم.
     */
    fun getStatus(
        token: String,
        cb: (Result<StatusResponse>) -> Unit
    ) {
        Thread {
            try {
                // بازیابی یوزر و پسورد از توکن جعلی
                val parts = token.split(":")
                if (parts.size < 2) {
                    cb(Result.failure(Exception("Invalid Credentials")))
                    return@Thread
                }
                val u = parts[0]
                val p = parts[1]

                val body = JSONObject().apply {
                    put("username", u)
                    put("password", p)
                }

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
                                
                                // استخراج اطلاعات ترافیک طبق خروجی panel.php
                                val traffic = data.optJSONObject("traffic")
                                val total = traffic?.optLong("total") ?: 0L
                                val used = traffic?.optLong("used") ?: 0L
                                val expire = traffic?.optLong("expire_ts") ?: 0L
                                
                                // لینک اشتراک
                                val subUrl = data.optString("subscription_url")
                                val linksList = if (subUrl.isNotEmpty()) listOf(subUrl) else emptyList()

                                val resp = StatusResponse(
                                    username = data.optString("username"),
                                    used_traffic = used,
                                    data_limit = total,
                                    expire = expire,
                                    status = "active",
                                    links = linksList,
                                    need_to_update = false,
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

    // متدهای زیر در پنل شما کاربردی ندارند، پس فقط پاسخ موفق الکی برمی‌گردانیم
    // تا برنامه کرش نکند.
    
    fun postKeepAlive(token: String, cb: (Result<Unit>) -> Unit) {
        cb(Result.success(Unit)) 
    }

    fun postUpdateFcmToken(token: String, fcmToken: String, cb: (Result<Unit>) -> Unit) {
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