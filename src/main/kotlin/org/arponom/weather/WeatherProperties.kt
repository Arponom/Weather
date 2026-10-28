package org.arponom.weather

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.math.BigDecimal

@Validated
@ConfigurationProperties("weather")
data class WeatherProperties(
    @field:NotBlank val city: String,
    @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: BigDecimal,
    @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: BigDecimal,
    @field:NotBlank val apiKey: String,
    val endpoint: String = "https://api.openweathermap.org/data/2.5/weather",
) {
    override fun toString(): String = "WeatherProperties(city=$city, latitude=$latitude, longitude=$longitude, apiKey=[REDACTED])"
}
