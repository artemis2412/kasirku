package com.artemis.kasirku.nativeapp.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BranchTimeTest {
    @Test
    fun businessDayStartsAtMidnightInBranchTimezone() {
        val zone = ZoneId.of("Asia/Makassar")
        val now = Instant.parse("2026-11-05T18:30:00Z")

        val start = BranchTime.startOfToday(zone, now)
        val end = BranchTime.startOfDayOffset(start, 1, zone)

        assertEquals(Instant.parse("2026-11-05T16:00:00Z").toEpochMilli(), start)
        assertEquals(Instant.parse("2026-11-06T16:00:00Z").toEpochMilli(), end)
    }

    @Test
    fun thirtyDayArchiveUsesCalendarDaysInBranchTimezone() {
        val zone = ZoneId.of("Asia/Jayapura")
        val todayStart = Instant.parse("2026-11-05T15:00:00Z").toEpochMilli()
        val archiveStart = BranchTime.startOfDayOffset(todayStart, -29, zone)

        assertEquals(
            Instant.parse("2026-10-07T15:00:00Z").toEpochMilli(),
            archiveStart
        )
    }
    @Test
    fun legacyBranchWithoutTimezoneUsesJakartaInsteadOfKasirDeviceTimezone() {
        val tenant = VerifiedTenantContext(
            userId = "user-1",
            storeId = "store-1",
            storeName = "Toko",
            role = StoreRole.CASHIER,
            branchId = "branch-1",
            branchName = "Cabang Lama",
            branchTimeZoneId = null
        )

        assertEquals(ZoneId.of("Asia/Jakarta"), BranchTime.zoneId(tenant))
    }

    @Test
    fun sameInstantUsesEachIndonesianBranchMidnight() {
        val instant = Instant.parse("2026-11-14T16:30:00Z")

        assertEquals(
            Instant.parse("2026-11-13T17:00:00Z").toEpochMilli(),
            BranchTime.startOfToday(ZoneId.of("Asia/Jakarta"), instant)
        )
        assertEquals(
            Instant.parse("2026-11-14T16:00:00Z").toEpochMilli(),
            BranchTime.startOfToday(ZoneId.of("Asia/Makassar"), instant)
        )
        assertEquals(
            Instant.parse("2026-11-14T15:00:00Z").toEpochMilli(),
            BranchTime.startOfToday(ZoneId.of("Asia/Jayapura"), instant)
        )
    }

    @Test
    fun configuredBranchTimezoneOverridesDeviceTimezone() {
        val tenant = VerifiedTenantContext(
            userId = "user-1",
            storeId = "store-1",
            storeName = "Toko",
            role = StoreRole.CASHIER,
            branchId = "branch-2",
            branchName = "Cabang Makassar",
            branchTimeZoneId = "Asia/Makassar"
        )

        assertEquals(ZoneId.of("Asia/Makassar"), BranchTime.zoneId(tenant))
    }

}
