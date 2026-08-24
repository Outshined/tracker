package org.paul.tracker.webdav

import java.security.cert.X509Certificate
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.Credentials
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebDavClient(
    private val httpFactory: (insecureTls: Boolean) -> OkHttpClient = { insecure ->
        defaultClient(insecure)
    },
) {
    data class Config(
        val url: String,
        val username: String,
        val password: String,
        val insecureTls: Boolean = false,
    )

    class HttpException(val code: Int, message: String) : RuntimeException(message)

    /** 404 → null. 3xx and other non-2xx → HttpException. */
    fun get(config: Config): ByteArray? {
        rejectColonUsername(config)
        val request = Request.Builder()
            .url(config.url)
            .get()
            .header("Authorization", Credentials.basic(config.username, config.password))
            .build()
        httpFactory(config.insecureTls).newCall(request).execute().use { response ->
            val code = response.code
            if (code == 404) return null
            if (code !in 200..299) {
                throw HttpException(code, "HTTP $code")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    /** 2xx only (200/201/204). */
    fun put(config: Config, body: ByteArray) {
        rejectColonUsername(config)
        val request = Request.Builder()
            .url(config.url)
            .put(body.toRequestBody(JSON_UTF8))
            .header("Authorization", Credentials.basic(config.username, config.password))
            .header("Overwrite", "T")
            .build()
        httpFactory(config.insecureTls).newCall(request).execute().use { response ->
            val code = response.code
            if (code !in PUT_OK) {
                throw HttpException(code, "HTTP $code")
            }
        }
    }
}

internal fun defaultClient(insecureTls: Boolean): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        // Non-daemon OkHttp dispatcher threads keep Gradle test workers alive after the suite.
        .dispatcher(Dispatcher(webDavExecutor()))
    if (insecureTls) {
        val trustAll = TrustAllManager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAll), null)
        builder.sslSocketFactory(sslContext.socketFactory, trustAll)
        builder.hostnameVerifier { _, _ -> true }
    }
    return builder.build()
}

private fun rejectColonUsername(config: WebDavClient.Config) {
    if (config.username.contains(':')) {
        throw IllegalArgumentException("username must not contain ':'")
    }
}

private fun webDavExecutor(): ThreadPoolExecutor =
    ThreadPoolExecutor(
        0,
        Int.MAX_VALUE,
        60,
        TimeUnit.SECONDS,
        SynchronousQueue(),
    ) { runnable ->
        Thread(runnable, "WebDav OkHttp").apply { isDaemon = true }
    }

private object TrustAllManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private val JSON_UTF8 = "application/json; charset=utf-8".toMediaType()

private val PUT_OK = setOf(200, 201, 204)
