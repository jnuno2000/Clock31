package com.dosse.clock31;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Content URI (as a string) that opens the calendar app at the given time, used to
     * make tapping the date jump to today's agenda. Calendar apps register ACTION_VIEW
     * for {@code content://com.android.calendar/time/<millis>}.
     */
    static String calendarTimeUri(long millis){
        return "content://com.android.calendar/time/" + millis;
    }

    // --- Agenda day-grouping + relative time -------------------------------------------

    /** Local calendar day of a timestamp, as a day count that increments at local midnight. */
    static long localEpochDay(long millis, TimeZone tz){
        return Math.floorDiv(millis + tz.getOffset(millis), 86400000L);
    }

    /**
     * Header label for the day an event begins: the given "today"/"tomorrow" labels for
     * those days (so the caller can localize them), otherwise a short date like
     * "Wed, Jul 23".
     */
    static String dayHeaderLabel(long beginMs, long nowMs, TimeZone tz, Locale locale,
                                 String todayLabel, String tomorrowLabel){
        long day = localEpochDay(beginMs, tz), today = localEpochDay(nowMs, tz);
        if(day == today) return todayLabel;
        if(day == today + 1) return tomorrowLabel;
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, MMM d", locale);
        fmt.setTimeZone(tz);
        return fmt.format(new java.util.Date(beginMs));
    }

    /** Whether two timestamps fall on the same local calendar day. */
    static boolean sameLocalDay(long a, long b, TimeZone tz){
        return localEpochDay(a, tz) == localEpochDay(b, tz);
    }

    enum RelKind { NOW, MINUTES, HOURS, NONE }

    /** A relative-time bucket for an event, e.g. NOW / MINUTES(25) / HOURS(3) / NONE. */
    static final class Relative {
        final RelKind kind; final int value;
        Relative(RelKind kind, int value){ this.kind = kind; this.value = value; }
    }

    /**
     * Relative-time label bucket for an upcoming event: NOW while it's ongoing, MINUTES
     * under an hour away, HOURS under a day away, else NONE (show the absolute time only).
     */
    static Relative relativeTime(long beginMs, long endMs, long nowMs){
        if(nowMs >= beginMs && nowMs < endMs) return new Relative(RelKind.NOW, 0);
        long d = beginMs - nowMs;
        if(d <= 0) return new Relative(RelKind.NONE, 0);
        if(d < 3600000L) return new Relative(RelKind.MINUTES, (int)Math.max(1, d / 60000L));
        if(d < 86400000L) return new Relative(RelKind.HOURS, (int)(d / 3600000L));
        return new Relative(RelKind.NONE, 0);
    }

    // --- Weather (Open-Meteo, keyless) -------------------------------------------------

    private static final Pattern TEMP_RE = Pattern.compile("\"temperature_2m\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern CODE_RE = Pattern.compile("\"weather_code\"\\s*:\\s*(\\d+)");

    /** Current temperature + WMO weather code parsed from an Open-Meteo response. */
    static final class WeatherData {
        final double temp; final int code;
        WeatherData(double temp, int code){ this.temp = temp; this.code = code; }
    }

    /** Open-Meteo current-weather URL (free, no API key). */
    static String openMeteoUrl(double lat, double lon, boolean celsius){
        return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code&temperature_unit=" + (celsius ? "celsius" : "fahrenheit");
    }

    /**
     * Extracts the current temperature and weather code from an Open-Meteo JSON response,
     * or null if it can't be parsed. Matches the numeric fields (the string values under
     * "current_units" are ignored because they aren't numbers).
     */
    static WeatherData parseOpenMeteoCurrent(String json){
        if(json == null) return null;
        Matcher t = TEMP_RE.matcher(json);
        Matcher c = CODE_RE.matcher(json);
        if(t.find() && c.find()){
            try { return new WeatherData(Double.parseDouble(t.group(1)), Integer.parseInt(c.group(1))); }
            catch(NumberFormatException e){ return null; }
        }
        return null;
    }

    /** Material Icons glyph (in material_weather.ttf) for a WMO weather code. */
    static String weatherGlyph(int wmoCode){
        if(wmoCode <= 1) return "\ue430";                                    // clear -> wb_sunny
        if(wmoCode <= 3) return "\ue42d";                                    // clouds -> wb_cloudy
        if(wmoCode == 45 || wmoCode == 48) return "\ue818";                  // fog -> foggy
        if(wmoCode >= 95) return "\uebdb";                                   // thunderstorm
        if((wmoCode >= 71 && wmoCode <= 77) || wmoCode == 85 || wmoCode == 86) return "\ueb3b"; // snow -> ac_unit
        if((wmoCode >= 51 && wmoCode <= 67) || (wmoCode >= 80 && wmoCode <= 82)) return "\uf1ad"; // rain -> umbrella
        return "\ue42d";                                                     // default -> wb_cloudy
    }

    /** Rounds a temperature to a whole-degree label, e.g. "21°". */
    static String formatTemp(double temp){
        return Math.round(temp) + "°";
    }
}
