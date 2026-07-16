package com.dosse.clock31;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches and caches the current weather from Open-Meteo (free, no API key). The parsing,
 * URL building and code→glyph mapping live in {@link Clock31Logic} (pure, unit-tested);
 * this class only does the Android I/O: last-known coarse location, an HTTP GET on a
 * background thread, a SharedPreferences cache, and a widget refresh when new data lands.
 */
final class WeatherRepository {

    private static final String TAG = "Clock31Weather";
    private static final String PREFS = "clock31_weather";
    private static final long MAX_AGE_MS = 30 * 60 * 1000L;      // refetch after 30 min
    private static final long SHOW_MAX_AGE_MS = 6 * 60 * 60 * 1000L; // hide if older than 6 h

    private WeatherRepository(){}

    /** Cached current weather, or null if none / too old to show. */
    static Clock31Logic.WeatherData getCached(Context ctx){
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long ts = p.getLong("ts", 0);
        if(ts == 0 || !p.contains("temp")) return null;
        if(System.currentTimeMillis() - ts > SHOW_MAX_AGE_MS) return null;
        return new Clock31Logic.WeatherData(p.getFloat("temp", 0f), p.getInt("code", 0));
    }

    /**
     * Kicks off a background refresh if the cache is stale. No-op when fresh, when the
     * location permission isn't granted, or when there's no last-known location.
     */
    static void refreshIfStale(Context ctx, boolean celsius){
        final Context app = ctx.getApplicationContext();
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if(System.currentTimeMillis() - p.getLong("ts", 0) < MAX_AGE_MS) return;
        if(!hasLocationPermission(app)) return;
        new Thread(new Runnable(){ public void run(){
            try {
                double[] loc = lastKnownLocation(app);
                if(loc == null) return;
                String json = httpGet(Clock31Logic.openMeteoUrl(loc[0], loc[1], celsius));
                Clock31Logic.WeatherData w = Clock31Logic.parseOpenMeteoCurrent(json);
                if(w == null) return;
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putFloat("temp", (float) w.temp)
                        .putInt("code", w.code)
                        .putLong("ts", System.currentTimeMillis())
                        .apply();
                app.sendBroadcast(new Intent(app, C31Widget.class).setAction(C31Widget.ACTION_REFRESH));
            } catch(Throwable t){
                Log.v(TAG, "weather fetch failed: " + t);
            }
        }}).start();
    }

    private static boolean hasLocationPermission(Context ctx){
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static double[] lastKnownLocation(Context ctx){
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if(lm == null) return null;
            Location best = null;
            for(String prov : lm.getProviders(true)){
                Location l = lm.getLastKnownLocation(prov);
                if(l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best == null ? null : new double[]{best.getLatitude(), best.getLongitude()};
        } catch(Throwable t){
            return null;
        }
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
