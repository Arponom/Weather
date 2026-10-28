package org.arponom.weather

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class OpenWeatherClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.start() }
    private val properties = WeatherProperties(
        "Москва", BigDecimal("55.7504461"), BigDecimal("37.6174943"), "secret+&key",
        endpoint = "http://127.0.0.1:${server.address.port}/data/2.5/weather",
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `requests metric temperature with encoded key and coordinates`() {
        var query = emptyMap<String, String>()
        server.createContext("/data/2.5/weather") { exchange ->
            query = exchange.requestURI.rawQuery.split("&").associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            }
            val body = """{"coord":{"lon":37.6174943,"lat":55.7504461},"main":{"temp":-12.375,"feels_like":-18.0,"temp_min":-15.0,"temp_max":-10.0,"pressure":1014},"dt":1793217600,"name":"Moscow","cod":200}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        assertEquals(BigDecimal("-12.375"), OpenWeatherClient(properties).fetchTemperatureCelsius())
        assertEquals("metric", query["units"])
        assertEquals("55.7504461", query["lat"])
        assertEquals("37.6174943", query["lon"])
        assertEquals("secret+&key", query["appid"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["{}", "{\"main\":{}}", "{\"main\":{\"temp\":true}}", "{\"main\":{\"temp\":null}}", "{\"main\":{\"temp\":\"12\"}}", "not json"])
    fun `rejects invalid or missing temperature`(body: String) {
        respond(200, body)
        assertThrows(WeatherApiException::class.java) { OpenWeatherClient(properties).fetchTemperatureCelsius() }
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 429, 500])
    fun `rejects upstream errors without exposing credentials`(status: Int) {
        respond(status, "secret+&key")
        val error = assertThrows(WeatherApiException::class.java) { OpenWeatherClient(properties).fetchTemperatureCelsius() }
        assertEquals("OpenWeather returned HTTP $status", error.message)
        assertNull(error.cause)
    }

    @Test
    fun `uses current weather endpoint by default`() {
        assertEquals("https://api.openweathermap.org/data/2.5/weather", properties.copy().let {
            WeatherProperties(it.city, it.latitude, it.longitude, it.apiKey).endpoint
        })
    }

    @Test
    fun `configuration does not expose key`() {
        assertFalse(properties.toString().contains(properties.apiKey))
    }

    private fun respond(status: Int, body: String) {
        server.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
