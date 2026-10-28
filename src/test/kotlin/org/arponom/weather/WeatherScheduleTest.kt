package org.arponom.weather

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.scheduling.support.CronExpression
import java.time.ZonedDateTime

class WeatherScheduleTest {
    @Test
    fun `default schedule runs at the start of each UTC hour including date rollover`() {
        val annotation = WeatherCollector::class.java.getMethod("collectScheduled")
            .getAnnotation(org.springframework.scheduling.annotation.Scheduled::class.java)
        assertEquals("UTC", annotation.zone)
        val cron = CronExpression.parse(annotation.cron.substringAfter(":").removeSuffix("}"))
        val next = cron.next(ZonedDateTime.parse("2026-10-28T23:35:00Z"))!!
        assertEquals(ZonedDateTime.parse("2026-10-29T00:00:00Z"), next)
        assertEquals(next.plusHours(1), cron.next(next))
    }
}
