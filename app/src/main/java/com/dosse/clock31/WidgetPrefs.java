package com.dosse.clock31;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Global, app-wide widget settings backed by SharedPreferences: the color tone, whether
 * the calendar is hidden (clock-only), and the weather on/off + unit. Defaults keep the
 * "automatic, no settings" behaviour (wallpaper accent, calendar shown, weather on, °C).
 */
final class WidgetPrefs {

    private static final String PREFS = "clock31_prefs";
    private static final String KEY_TONE = "color_tone";
    private static final String KEY_CLOCK_ONLY = "clock_only";
    private static final String KEY_WEATHER = "weather_enabled";
    private static final String KEY_CELSIUS = "celsius";
    private static final String KEY_WEATHER_APP = "weather_app";

    private WidgetPrefs(){}

    private static SharedPreferences prefs(Context ctx){
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int colorTone(Context ctx){ return prefs(ctx).getInt(KEY_TONE, Clock31Logic.TONE_ACCENT); }
    static boolean clockOnly(Context ctx){ return prefs(ctx).getBoolean(KEY_CLOCK_ONLY, false); }
    static boolean weatherEnabled(Context ctx){ return prefs(ctx).getBoolean(KEY_WEATHER, true); }
    static boolean celsius(Context ctx){ return prefs(ctx).getBoolean(KEY_CELSIUS, true); }

    /** Package of the app to open when the weather is tapped; "" if none chosen. */
    static String weatherApp(Context ctx){ return prefs(ctx).getString(KEY_WEATHER_APP, ""); }
    static void setWeatherApp(Context ctx, String pkg){ prefs(ctx).edit().putString(KEY_WEATHER_APP, pkg == null ? "" : pkg).apply(); }

    static void save(Context ctx, int tone, boolean clockOnly, boolean weatherEnabled, boolean celsius){
        prefs(ctx).edit()
                .putInt(KEY_TONE, tone)
                .putBoolean(KEY_CLOCK_ONLY, clockOnly)
                .putBoolean(KEY_WEATHER, weatherEnabled)
                .putBoolean(KEY_CELSIUS, celsius)
                .apply();
    }
}
