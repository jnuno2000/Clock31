package com.dosse.clock31;

/**
 * Pure, Android-free logic extracted from the widget so it can be unit-tested on a plain
 * JVM (see {@code app/src/test}). Everything here is deterministic and side-effect free.
 *
 * IMPORTANT: do not add any {@code android.*} imports to this file — keeping it Android-free
 * is what lets these tests run fast in CI (and locally via javac) and guard against
 * regressions in the widget's sizing/formatting decisions.
 */
final class Clock31Logic {

    private Clock31Logic(){}

    /** Result of {@link #sizingFor(int, int)}. */
    static final class Sizing {
        final float clockScale, dateScale;
        final boolean hideAlarm, hideCalendar;
        Sizing(float clockScale, float dateScale, boolean hideAlarm, boolean hideCalendar){
            this.clockScale=clockScale;
            this.dateScale=dateScale;
            this.hideAlarm=hideAlarm;
            this.hideCalendar=hideCalendar;
        }
    }

    /**
     * Font scales and hide flags derived from the widget's reported min width/height (dp).
     * Mirrors the original inline logic in {@code renderClockDate}. The extra SDK&lt;=N
     * 0.8x clock tweak stays in the caller because it depends on the runtime SDK.
     */
    static Sizing sizingFor(int w, int h){
        float clockScale=Math.max(0.4f, Math.min(1f, Math.min(h, w) / 275f));
        float dateScale=Math.max(0.7f, Math.min(1f, Math.min(h, w) / 275f));
        boolean hideAlarm=false, hideCalendar=false;
        if(w<150){ hideAlarm=true; dateScale=Math.min(1f, dateScale*1.25f); }
        if(h<80){ hideCalendar=true; clockScale=Math.min(1f, clockScale*1.5f); dateScale=Math.min(1f, dateScale*1.25f); }
        return new Sizing(clockScale, dateScale, hideAlarm, hideCalendar);
    }

    /**
     * Largest whole number of calendar blocks that fit in {@code availablePx}. Each
     * {@code blockPx} already includes one inter-block divider, so N blocks occupy
     * {@code N*blockPx - dividerPx} (they share N-1 dividers).
     */
    static int blocksThatFit(float availablePx, int blockPx, int dividerPx){
        if(blockPx<=0) return 1;
        return Math.max(1, (int)Math.floor((availablePx + dividerPx) / (float) blockPx));
    }

    /** Pixel height of a list showing {@code n} whole blocks (n blocks use n-1 dividers). */
    static int listHeightPx(int n, int blockPx, int dividerPx){
        return Math.max(1, n*blockPx - dividerPx);
    }

    /** Clock time pattern: 24-hour vs 12-hour (no AM/PM — that renders separately). */
    static String clockFormatPattern(boolean is24){ return is24 ? "HH:mm" : "h:mm"; }

    /** Whether the separate AM/PM view should be shown (only in 12-hour mode). */
    static boolean ampmVisible(boolean is24){ return !is24; }

    /** Next-alarm pattern, e.g. "Thu 07:00" (24h) or "Thu 7:00 AM" (12h). */
    static String alarmFormatPattern(boolean is24){ return is24 ? "E HH:mm" : "E h:mm a"; }

    /** Date line pattern, e.g. "Jul 15, Wed". */
    static final String DATE_FORMAT_PATTERN = "MMM d, EEE";

    /** Epoch millis of the start of the next minute strictly after {@code now}. */
    static long nextMinuteBoundary(long now){ return now - (now % 60000L) + 60000L; }

    /**
     * Fill color for an event block: fall back to {@code defaultColor} when the event's
     * calendar color has zero alpha (i.e. no color set).
     */
    static int blockColor(int eventColor, int defaultColor){
        return (eventColor >>> 24) == 0 ? defaultColor : eventColor;
    }
}
