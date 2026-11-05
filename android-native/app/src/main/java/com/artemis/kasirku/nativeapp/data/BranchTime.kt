package com.artemis.kasirku.nativeapp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BranchTime {
    fun zoneId(tenant: VerifiedTenantContext): ZoneId =
        tenant.branchTimeZoneId
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()

    fun startOfToday(zoneId: ZoneId, now: Instant = Instant.now()): Long =
        now.atZone(zoneId).toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()

    fun startOfDayOffset(startOfTodayMillis: Long, days: Long, zoneId: ZoneId): Long {
        val date = Instant.ofEpochMilli(startOfTodayMillis).atZone(zoneId).toLocalDate()
        return date.plusDays(days).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun format(epochMillis: Long, pattern: String, zoneId: ZoneId): String =
        DateTimeFormatter.ofPattern(pattern, Locale("id", "ID")).withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))
}
