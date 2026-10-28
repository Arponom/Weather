package org.arponom.weather

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = ["weather.cron=-"])
class WeatherCollectorIT {
    @Autowired lateinit var collector: WeatherCollector
    @Autowired lateinit var jdbc: JdbcTemplate

    companion object {
        @Volatile private var status = 200
        @Volatile private var response = """{"main":{"temp":-7.125}}"""
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also {
            it.createContext("/") { exchange ->
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { out -> out.write(bytes) }
            }
            it.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configure(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { System.getenv("TEST_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/weather_test" }
            registry.add("spring.datasource.username") { System.getenv("TEST_DATABASE_USER") ?: "weather" }
            registry.add("spring.datasource.password") { System.getenv("TEST_DATABASE_PASSWORD") ?: "weather-local-only" }
            registry.add("weather.city") { "Москва" }
            registry.add("weather.latitude") { "55.7504461" }
            registry.add("weather.longitude") { "37.6174943" }
            registry.add("weather.api-key") { "test-key" }
            registry.add("weather.endpoint") { "http://127.0.0.1:${server.address.port}/weather" }
        }

        @JvmStatic @AfterAll
        fun stop() = server.stop(0)
    }

    @BeforeEach
    fun clear() {
        jdbc.update("DELETE FROM weather_reading")
        status = 200
        response = """{"main":{"temp":-7.125}}"""
    }

    @Test
    fun `saves separate request time city and celsius columns in Postgres`() {
        val before = Instant.now()
        collector.collect()
        val row = jdbc.queryForMap("SELECT requested_at, city, temperature_celsius FROM weather_reading")
        assertEquals("Москва", row["city"])
        assertEquals(0, BigDecimal("-7.125").compareTo(row["temperature_celsius"] as BigDecimal))
        val timestamp = (row["requested_at"] as java.sql.Timestamp).toInstant()
        assertFalse(timestamp.isBefore(before))
        assertFalse(timestamp.isAfter(Instant.now()))
    }

    @Test
    fun `failed API request leaves database unchanged and next collection succeeds`() {
        status = 429
        collector.collectScheduled()
        assertEquals(0L, count())
        status = 200
        collector.collectScheduled()
        assertEquals(1L, count())
    }

    @Test
    fun `invalid response is never saved`() {
        response = """{"main":{"temp":null}}"""
        assertThrows(WeatherApiException::class.java) { collector.collect() }
        assertEquals(0L, count())
    }

    @Test
    fun `retains history across hourly collections`() {
        collector.collect()
        response = """{"main":{"temp":0}}"""
        collector.collect()
        assertEquals(2L, count())
    }

    private fun count(): Long = jdbc.queryForObject("SELECT count(*) FROM weather_reading", Long::class.java)!!
}
