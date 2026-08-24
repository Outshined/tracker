package org.paul.tracker.webdav

import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSession
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavClientTest {
    private lateinit var server: MockWebServer
    private val client = WebDavClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `T-put sends method body basic auth json content-type overwrite T`() {
        server.enqueue(MockResponse().setResponseCode(201))
        val body = """{"exportedAt":"2026-08-24T12:00:00Z"}""".toByteArray(StandardCharsets.UTF_8)

        client.put(config(), body)

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/remote.php/dav/files/paul/tracker.json", recorded.path)
        assertArrayEquals(body, recorded.body.readByteArray())
        assertEquals("Basic cGF1bDpzZWNyZXQ=", recorded.getHeader("Authorization"))
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        assertEquals("T", recorded.getHeader("Overwrite"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `T-get-200-returns-body`() {
        val payload = """{"metrics":[]}""".toByteArray(StandardCharsets.UTF_8)
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(payload, StandardCharsets.UTF_8)))

        val got = client.get(config())

        assertNotNull(got)
        assertArrayEquals(payload, got)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/remote.php/dav/files/paul/tracker.json", recorded.path)
        assertEquals("Basic cGF1bDpzZWNyZXQ=", recorded.getHeader("Authorization"))
    }

    @Test
    fun `T-get-404-returns-null`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("missing"))

        assertNull(client.get(config()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `T-get-401-throws`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("auth"))

        val thrown = assertThrows(WebDavClient.HttpException::class.java) {
            client.get(config())
        }
        assertEquals(401, thrown.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `T-put-500-throws`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("fail"))

        val thrown = assertThrows(WebDavClient.HttpException::class.java) {
            client.put(config(), "{}".toByteArray(StandardCharsets.UTF_8))
        }
        assertEquals(500, thrown.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `T-302-no-second-request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", server.url("/other").toString()),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("should-not-follow"))

        val thrown = assertThrows(WebDavClient.HttpException::class.java) {
            client.get(config())
        }
        assertEquals(302, thrown.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `T-username-colon-iae-before-io`() {
        assertThrows(IllegalArgumentException::class.java) {
            client.get(config().copy(username = "user:name"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.put(config().copy(username = "user:name"), byteArrayOf(1))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `T-default-factory-no-trust-all`() {
        var seenInsecure: Boolean? = null
        var built: OkHttpClient? = null
        val dav = WebDavClient { insecure ->
            seenInsecure = insecure
            defaultClient(insecure).also { built = it }
        }
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        dav.get(config().copy(insecureTls = false))
        assertEquals(false, seenInsecure)

        val secure = built!!
        val insecure = defaultClient(true)
        try {
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            assertTrue(insecure.hostnameVerifier.verify("evil.example", null as SSLSession?))
            val secureTrustsAll = try {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                secure.hostnameVerifier.verify("evil.example", null as SSLSession?)
            } catch (_: Exception) {
                false
            }
            assertFalse(secureTrustsAll)
        } finally {
            secure.dispatcher.executorService.shutdown()
            secure.connectionPool.evictAll()
            insecure.dispatcher.executorService.shutdown()
            insecure.connectionPool.evictAll()
        }
    }

    private fun config() = WebDavClient.Config(
        url = server.url("/remote.php/dav/files/paul/tracker.json").toString(),
        username = "paul",
        password = "secret",
    )
}
