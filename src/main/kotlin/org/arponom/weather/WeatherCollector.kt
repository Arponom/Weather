package org.arponom.weather

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

@Component
class WeatherCollector(
    private val client: OpenWeatherClient,
    private val jdbc: JdbcTemplate,
    private val properties: WeatherProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${weather.cron:0 0 * * * *}", zone = "UTC")
    fun collectScheduled() {
        try {
            collect()
        } catch (_: Exception) {
            // Do not log exception details: HTTP request diagnostics may expose appid.
            logger.error("Weather collection failed; no reading was saved. Next attempt follows the schedule.")
        }
    }

    fun collect() {
        val requestedAt = OffsetDateTime.now(clock)
        val temperature = client.fetchTemperatureCelsius()
        jdbc.update(
            "INSERT INTO weather_reading (requested_at, city, temperature_celsius) VALUES (?, ?, ?)",
            requestedAt, properties.city, temperature,
        )
        logger.info("Weather reading saved for {}", properties.city)
    }
}
