package com.dosse.clock31;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

/**
 * Pure-logic regression tests for the existing widget behaviour. These run on a plain JVM
 * (no Android), so they're fast and guard the sizing/formatting decisions that visual
 * inspection can't easily catch.
 */
public class Clock31LogicTest {

    // --- Widget-size scaling + hide thresholds -----------------------------------------

    @Test public void wideWidget_showsEverything_scaleClampedToOne() {
        Clock31Logic.Sizing s = Clock31Logic.sizingFor(300, 300);
        assertFalse(s.hideAlarm);
        assertFalse(s.hideCalendar);
        assertEquals(1f, s.clockScale, 0.0001f);
        assertEquals(1f, s.dateScale, 0.0001f);
    }

    @Test public void narrowWidget_hidesAlarm_at149() {
        assertTrue(Clock31Logic.sizingFor(149, 300).hideAlarm);
        assertFalse(Clock31Logic.sizingFor(150, 300).hideAlarm); // boundary: 150 keeps the alarm
    }

    @Test public void shortWidget_hidesCalendar_below80() {
        assertTrue(Clock31Logic.sizingFor(300, 79).hideCalendar);
        assertFalse(Clock31Logic.sizingFor(300, 80).hideCalendar);
    }

    @Test public void scales_respectLowerClamps() {
        Clock31Logic.Sizing tiny = Clock31Logic.sizingFor(200, 200); // min/275 = 0.727
        assertEquals(0.727f, tiny.clockScale, 0.001f);
        assertEquals(0.727f, tiny.dateScale, 0.001f);
        // Clock never drops below 0.4 even for a very small widget (before the h<80 boost).
        assertTrue(Clock31Logic.sizingFor(160, 90).clockScale >= 0.4f);
    }

    // --- Whole-block calendar sizing ---------------------------------------------------

    @Test public void blocksThatFit_picksLargestWholeCount() {
        assertEquals(3, Clock31Logic.blocksThatFit(180f, 60, 6)); // (180+6)/60 = 3.1 -> 3
        assertEquals(2, Clock31Logic.blocksThatFit(170f, 60, 6)); // (170+6)/60 = 2.93 -> 2
    }

    @Test public void blocksThatFit_neverZero_andHandlesBadBlock() {
        assertEquals(1, Clock31Logic.blocksThatFit(0f, 60, 6));
        assertEquals(1, Clock31Logic.blocksThatFit(1000f, 0, 6)); // blockPx<=0 guard
    }

    @Test public void listHeight_isNBlocksMinusOneDivider() {
        assertEquals(174, Clock31Logic.listHeightPx(3, 60, 6)); // 3*60 - 6
        assertEquals(54, Clock31Logic.listHeightPx(1, 60, 6));  // single block, no divider subtracted below 1
        assertEquals(1, Clock31Logic.listHeightPx(0, 60, 6));   // clamped to >=1
    }

    // --- Clock / alarm / date formats --------------------------------------------------

    @Test public void clockPattern_and_ampm_by24h() {
        assertEquals("HH:mm", Clock31Logic.clockFormatPattern(true));
        assertEquals("h:mm", Clock31Logic.clockFormatPattern(false));
        assertFalse(Clock31Logic.ampmVisible(true));
        assertTrue(Clock31Logic.ampmVisible(false));
    }

    @Test public void alarmPattern_by24h() {
        assertEquals("E HH:mm", Clock31Logic.alarmFormatPattern(true));
        assertEquals("E h:mm a", Clock31Logic.alarmFormatPattern(false));
    }

    @Test public void datePattern_isLockScreenStyle() {
        assertEquals("MMM d, EEE", Clock31Logic.DATE_FORMAT_PATTERN);
    }

    // --- Per-minute tick boundary ------------------------------------------------------

    @Test public void nextMinuteBoundary_roundsUpToNextMinute() {
        assertEquals(120000L, Clock31Logic.nextMinuteBoundary(90000L));   // 1:30 -> 2:00
        assertEquals(180000L, Clock31Logic.nextMinuteBoundary(120000L));  // exact minute -> strictly next
    }

    // --- Event block color fallback ----------------------------------------------------

    @Test public void blockColor_fallsBackWhenNoAlpha() {
        assertEquals(0xFF757575, Clock31Logic.blockColor(0x00123456, 0xFF757575)); // zero alpha -> default
        assertEquals(0xFF00FF00, Clock31Logic.blockColor(0xFF00FF00, 0xFF757575)); // real color passes through
    }

    // --- Tap the date -> open calendar at today (B1) -----------------------------------

    @Test public void calendarTimeUri_pointsAtCalendarTimeEndpoint() {
        assertEquals("content://com.android.calendar/time/1500000000000",
                Clock31Logic.calendarTimeUri(1500000000000L));
    }

    // --- Agenda day-grouping + relative time (B2) --------------------------------------

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long NOW = 1500000000000L; // 2017-07-14 02:40:00 UTC

    @Test public void localEpochDay_incrementsAtMidnight() {
        assertEquals(Clock31Logic.localEpochDay(NOW, UTC),
                Clock31Logic.localEpochDay(NOW + 3600000L, UTC));            // same day
        assertEquals(Clock31Logic.localEpochDay(NOW, UTC) + 1,
                Clock31Logic.localEpochDay(NOW + 86400000L, UTC));          // next day
    }

    @Test public void dayHeaderLabel_todayTomorrowElseDate() {
        assertEquals("Today", Clock31Logic.dayHeaderLabel(NOW + 3600000L, NOW, UTC, Locale.US, "Today", "Tomorrow"));
        assertEquals("Tomorrow", Clock31Logic.dayHeaderLabel(NOW + 86400000L, NOW, UTC, Locale.US, "Today", "Tomorrow"));
        assertEquals("Mon, Jul 17", Clock31Logic.dayHeaderLabel(NOW + 3 * 86400000L, NOW, UTC, Locale.US, "Today", "Tomorrow"));
    }

    @Test public void relativeTime_buckets() {
        assertEquals(Clock31Logic.RelKind.NOW, Clock31Logic.relativeTime(NOW - 1000L, NOW + 1000L, NOW).kind);
        Clock31Logic.Relative min = Clock31Logic.relativeTime(NOW + 30 * 60000L, NOW + 90 * 60000L, NOW);
        assertEquals(Clock31Logic.RelKind.MINUTES, min.kind);
        assertEquals(30, min.value);
        Clock31Logic.Relative hr = Clock31Logic.relativeTime(NOW + 90 * 60000L, NOW + 120 * 60000L, NOW);
        assertEquals(Clock31Logic.RelKind.HOURS, hr.kind);
        assertEquals(1, hr.value);
        assertEquals(Clock31Logic.RelKind.NONE, Clock31Logic.relativeTime(NOW + 2 * 86400000L, NOW + 2 * 86400000L + 3600000L, NOW).kind);
        assertEquals(Clock31Logic.RelKind.NONE, Clock31Logic.relativeTime(NOW - 1000L, NOW - 500L, NOW).kind); // past
    }
}
