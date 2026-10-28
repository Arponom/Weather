package org.arponom.weather

import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class WeatherApiException(message: String) : RuntimeException(message)

@Component
class OpenWeatherClient(private val properties: WeatherProperties) {
    private val mapper = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .apply {
            // Java does not automatically use HTTPS_PROXY. Preserve the managed proxy route.
            val uri = URI.create(properties.endpoint)
            require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1"))) {
                "OpenWeather endpoint must use HTTPS (HTTP is allowed only for local tests)"
            }
            if (uri.host !in setOf("localhost", "127.0.0.1")) {
                System.getenv("HTTPS_PROXY")?.takeIf { it.isNotBlank() }?.let {
                    val proxy = URI.create(it)
                    proxy(ProxySelector.of(InetSocketAddress(proxy.host, if (proxy.port == -1) 80 else proxy.port)))
                }
            }
        }
        .build()

    fun fetchTemperatureCelsius(): BigDecimal {
        val uri = UriComponentsBuilder.fromUriString(properties.endpoint)
            .queryParam("lat", properties.latitude)
            .queryParam("lon", properties.longitude)
            .queryParam("appid", "{apiKey}")
            .queryParam("units", "metric")
            .encode().buildAndExpand(mapOf("apiKey" to properties.apiKey)).toUri()
        val response = try {
            client.send(
                HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw WeatherApiException("OpenWeather request interrupted")
        } catch (_: Exception) {
            // Request URI and upstream error bodies can contain the API key. Never retain them.
            throw WeatherApiException("OpenWeather connection failed")
        }
        if (response.statusCode() != 200) {
            throw WeatherApiException("OpenWeather returned HTTP ${response.statusCode()}")
        }
        return try {
            val node = mapper.readTree(response.body()).at("/main/temp")
            if (!node.isNumber) throw WeatherApiException("OpenWeather temperature is missing or is not numeric")
            node.decimalValue()
        } catch (e: WeatherApiException) {
            throw e
        } catch (_: Exception) {
            throw WeatherApiException("OpenWeather returned invalid JSON")
        }
    }
}
