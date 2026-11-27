package com.nova.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

public class DateTimeSpecTest {
    private static final DateTimeSpec.Kind DATE = DateTimeSpec.Kind.DATE;
    private static final DateTimeSpec.Kind TIME = DateTimeSpec.Kind.TIME;
    private static final DateTimeSpec.Kind DTL = DateTimeSpec.Kind.DATETIME_LOCAL;
    private static final DateTimeSpec.Kind MONTH = DateTimeSpec.Kind.MONTH;
    private static final DateTimeSpec.Kind WEEK = DateTimeSpec.Kind.WEEK;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 14, 37, 21);

    @Test
    public void parsesAllKinds() {
        assertEquals(LocalDateTime.of(2024, 2, 29, 0, 0), DateTimeSpec.parse(DATE, "2024-02-29"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 9, 5), DateTimeSpec.parse(TIME, "09:05"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 9, 5), DateTimeSpec.parse(TIME, "09:05:59"));
        assertEquals(LocalDateTime.of(2024, 5, 6, 12, 30), DateTimeSpec.parse(DTL, "2024-05-06T12:30"));
        assertEquals(LocalDateTime.of(2024, 5, 6, 12, 30), DateTimeSpec.parse(DTL, "2024-05-06T12:30:45"));
        assertEquals(LocalDateTime.of(2024, 5, 6, 12, 30), DateTimeSpec.parse(DTL, "2024-05-06 12:30"));
        assertEquals(LocalDateTime.of(2024, 5, 1, 0, 0), DateTimeSpec.parse(MONTH, "2024-05"));
        assertEquals(LocalDateTime.of(2024, 1, 29, 0, 0), DateTimeSpec.parse(WEEK, "2024-W05"));
    }

    @Test
    public void rejectsInvalid() {
        assertNull(DateTimeSpec.parse(DATE, null));
        assertNull(DateTimeSpec.parse(DATE, ""));
        assertNull(DateTimeSpec.parse(DATE, "2023-02-29"));
        assertNull(DateTimeSpec.parse(DATE, "not a date"));
        assertNull(DateTimeSpec.parse(TIME, "25:00"));
        assertNull(DateTimeSpec.parse(DTL, "2024-05-06"));
        assertNull(DateTimeSpec.parse(MONTH, "2024-13"));
        assertNull(DateTimeSpec.parse(WEEK, "2024-W00"));
        assertNull(DateTimeSpec.parse(WEEK, "2024-W54"));
        assertNull(DateTimeSpec.parse(WEEK, "2023-W53"));
        assertNull(DateTimeSpec.parse(WEEK, "2024-5"));
    }

    @Test
    public void weekFiftyThreeOnlyInLongYears() {
        assertEquals(LocalDateTime.of(2020, 12, 28, 0, 0), DateTimeSpec.parse(WEEK, "2020-W53"));
        assertEquals(LocalDateTime.of(2024, 12, 30, 0, 0), DateTimeSpec.parse(WEEK, "2025-W01"));
    }

    @Test
    public void formatsAllKinds() {
        assertEquals("2026-11-26", DateTimeSpec.format(DATE, NOW));
        assertEquals("14:37", DateTimeSpec.format(TIME, NOW));
        assertEquals("2026-11-26T14:37", DateTimeSpec.format(DTL, NOW));
        assertEquals("2026-11", DateTimeSpec.format(MONTH, NOW));
        assertEquals("2026-W48", DateTimeSpec.format(WEEK, NOW));
        assertEquals("2021-W01", DateTimeSpec.format(WEEK, LocalDateTime.of(2021, 1, 4, 0, 0)));
        assertEquals("2020-W53", DateTimeSpec.format(WEEK, LocalDateTime.of(2021, 1, 3, 10, 0)));
        assertEquals("0999-01-02", DateTimeSpec.format(DATE, LocalDateTime.of(999, 1, 2, 0, 0)));
    }

    @Test
    public void formatParseRoundTrip() {
        for (DateTimeSpec.Kind k : DateTimeSpec.Kind.values()) {
            String s = DateTimeSpec.format(k, NOW);
            assertEquals(s, DateTimeSpec.format(k, DateTimeSpec.parse(k, s)));
        }
    }

    @Test
    public void initialUsesDefaultWhenValid() {
        assertEquals(LocalDateTime.of(2024, 2, 29, 0, 0), DateTimeSpec.initial(DATE, "2024-02-29", null, null, NOW));
        assertEquals(LocalDateTime.of(1970, 1, 1, 8, 15), DateTimeSpec.initial(TIME, "08:15", null, null, NOW));
        assertEquals(LocalDateTime.of(2030, 1, 2, 3, 4), DateTimeSpec.initial(DTL, "2030-01-02T03:04", null, null, NOW));
    }

    @Test
    public void initialFallsBackToNow() {
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0), DateTimeSpec.initial(DATE, "", null, null, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0), DateTimeSpec.initial(DATE, "garbage", null, null, NOW));
        assertEquals(LocalDateTime.of(1970, 1, 1, 14, 37), DateTimeSpec.initial(TIME, null, null, null, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 26, 14, 37), DateTimeSpec.initial(DTL, null, null, null, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), DateTimeSpec.initial(MONTH, null, null, null, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), DateTimeSpec.initial(WEEK, null, null, null, NOW));
    }

    @Test
    public void initialIsClampedToRange() {
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), DateTimeSpec.initial(DATE, null, "2027-01-01", "2027-12-31", NOW));
        assertEquals(LocalDateTime.of(2026, 6, 30, 0, 0), DateTimeSpec.initial(DATE, null, "2026-01-01", "2026-06-30", NOW));
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0), DateTimeSpec.initial(DATE, "2026-11-26", "2026-01-01", "2026-12-31", NOW));
        assertEquals(LocalDateTime.of(2025, 3, 1, 0, 0), DateTimeSpec.initial(MONTH, "2020-01", "2025-03", null, NOW));
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0), DateTimeSpec.initial(WEEK, null, null, "2026-W47", NOW));
    }

    @Test
    public void invalidBoundsAreIgnored() {
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0), DateTimeSpec.initial(DATE, null, "bad", "worse", NOW));
        assertEquals(LocalDateTime.of(2026, 11, 26, 0, 0), DateTimeSpec.initial(DATE, null, "2027-01-01", "2026-01-01", NOW));
    }

    @Test
    public void timeRangeClamps() {
        assertEquals(LocalDateTime.of(1970, 1, 1, 9, 0), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 8, 0), "09:00", "17:00"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 17, 0), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 18, 30), "09:00", "17:00"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 12, 0), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 12, 0), "09:00", "17:00"));
    }

    @Test
    public void timeRangeWrappingMidnight() {
        assertEquals(LocalDateTime.of(1970, 1, 1, 23, 30), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 23, 30), "22:00", "06:00"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 3, 0), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 3, 0), "22:00", "06:00"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 22, 0), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 15, 0), "22:00", "06:00"));
        assertEquals(LocalDateTime.of(1970, 1, 1, 6, 0), DateTimeSpec.clamp(TIME, LocalDateTime.of(1970, 1, 1, 9, 0), "22:00", "06:00"));
    }

    @Test
    public void datetimeLocalRange() {
        LocalDateTime v = LocalDateTime.of(2026, 1, 1, 10, 0);
        assertEquals(LocalDateTime.of(2026, 3, 1, 8, 0), DateTimeSpec.clamp(DTL, v, "2026-03-01T08:00", "2026-04-01T08:00"));
        assertEquals(LocalDateTime.of(2025, 12, 31, 23, 59), DateTimeSpec.clamp(DTL, v, null, "2025-12-31T23:59"));
    }

    @Test
    public void weekAndMonthBoundsNormalizeToPeriodStart() {
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), DateTimeSpec.clamp(WEEK, LocalDateTime.of(2026, 11, 29, 0, 0), "2026-W48", "2026-W48"));
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), DateTimeSpec.clamp(MONTH, LocalDateTime.of(2026, 11, 30, 0, 0), "2026-11", "2026-11"));
    }

    @Test
    public void pickerBoundsCoverWholePeriod() {
        assertEquals(LocalDateTime.of(2026, 6, 30, 23, 59, 59, 999_999_999), DateTimeSpec.maxEnd(DATE, "2026-06-30"));
        assertEquals(LocalDateTime.of(2026, 2, 28, 23, 59, 59, 999_999_999), DateTimeSpec.maxEnd(MONTH, "2026-02"));
        assertEquals(LocalDateTime.of(2026, 11, 29, 23, 59, 59, 999_999_999), DateTimeSpec.maxEnd(WEEK, "2026-W48"));
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0), DateTimeSpec.minStart(WEEK, "2026-W48"));
        assertNull(DateTimeSpec.maxEnd(DATE, null));
        assertNull(DateTimeSpec.minStart(DATE, "x"));
    }

    @Test
    public void epochMillisUsesZone() {
        assertEquals(0L, DateTimeSpec.epochMillis(LocalDateTime.of(1970, 1, 1, 0, 0), ZoneOffset.UTC));
        assertEquals(-3_600_000L, DateTimeSpec.epochMillis(LocalDateTime.of(1970, 1, 1, 0, 0), ZoneOffset.ofHours(1)));
    }
}
