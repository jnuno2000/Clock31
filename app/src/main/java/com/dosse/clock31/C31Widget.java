package com.dosse.clock31;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.text.format.DateFormat;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link C31WidgetConfigureActivity C31WidgetConfigureActivity}
 *
 * The clock, date and alarm are drawn to bitmaps using the bundled MiSans font
 * (plus the Material Icons alarm glyph), because home-screen widgets can't reliably
 * apply custom fonts to text views. The clock bitmap is refreshed once a minute via
 * {@link #ACTION_TICK}.
 */
public class C31Widget extends AppWidgetProvider {

    private static final String TAG="C31WidgetProvider";

    public static boolean updatePending =false;

    // Exact height (px) of one calendar block incl. its gap, reported by
    // CalendarRemoteViewsService once it has rendered a real event. Used to size the
    // calendar list to a whole number of blocks so no partial block shows at rest.
    public static volatile int calendarBlockHeightPx = 0;

    public static final String ACTION_REFRESH="com.dosse.clock31.ACTION_REFRESH";
    public static final String ACTION_TICK="com.dosse.clock31.ACTION_TICK";

    private static Typeface clockTypeface, dateTypeface, alarmIconTypeface;

    /** Material Icons "alarm" glyph, drawn as a tintable monochrome font glyph. */
    private static final String ALARM_GLYPH = "\ue855";

    private static Typeface alarmIconTypeface(Context context){
        if(alarmIconTypeface==null){
            try{ alarmIconTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/material_alarm.ttf"); }
            catch(Throwable t){ alarmIconTypeface=Typeface.DEFAULT; }
        }
        return alarmIconTypeface;
    }

    private static Typeface weatherTypeface;

    private static Typeface weatherTypeface(Context context){
        if(weatherTypeface==null){
            try{ weatherTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/material_weather.ttf"); }
            catch(Throwable t){ weatherTypeface=Typeface.DEFAULT; }
        }
        return weatherTypeface;
    }

    private static Typeface clockTypeface(Context context){
        if(clockTypeface==null){
            try{ clockTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/mi_sans.ttf"); }
            catch(Throwable t){ clockTypeface=Typeface.DEFAULT; }
        }
        return clockTypeface;
    }

    private static Typeface dateTypeface(Context context){
        if(dateTypeface==null){
            try{ dateTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/mi_sans.ttf"); }
            catch(Throwable t){ dateTypeface=Typeface.DEFAULT; }
        }
        return dateTypeface;
    }

    static int resolveColor(Context context, int resId){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M) return context.getColor(resId);
        return context.getResources().getColor(resId);
    }

    private static Typeface gsTitleTf, gsTimeTf;

    /**
     * Height (px) of one calendar block, mirroring CalendarRemoteViewsService's event
     * bitmap sizing (Google Sans title + time + padding 9+9, title/time margin 1,
     * divider 6). Sizes must match renderEventText. Used to pre-size the list before
     * the calendar has rendered.
     */
    private static int computeCalendarBlockHeightPx(Context context, float density){
        try{
            if(gsTitleTf==null) gsTitleTf=Typeface.createFromAsset(context.getAssets(),"fonts/google_sans_medium.ttf");
            if(gsTimeTf==null) gsTimeTf=Typeface.createFromAsset(context.getAssets(),"fonts/google_sans_regular.ttf");
        }catch(Throwable t){}
        return oneLineBitmapHeight(gsTitleTf, 15f*density) + oneLineBitmapHeight(gsTimeTf, 12f*density)
                + (int)Math.ceil((7f+7f+1f+6f)*density);
    }

    private static int oneLineBitmapHeight(Typeface tf, float sizePx){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf!=null?tf:Typeface.DEFAULT);
        p.setTextSize(sizePx);
        Paint.FontMetrics fm=p.getFontMetrics();
        int pad=Math.max(2,(int)Math.ceil(sizePx*0.06f)); // matches renderEventText's pad
        return (int)Math.ceil(fm.descent-fm.ascent)+pad*2;
    }

    /**
     * Renders text to a bitmap using the given typeface, with an optional smaller
     * suffix (e.g. AM/PM) in a second typeface baseline-aligned next to it, plus a
     * soft drop shadow so light text stays legible over any wallpaper.
     */
    static Bitmap renderText(Context context, CharSequence main, Typeface mainTf, float mainPx,
                                     CharSequence suffix, Typeface suffixTf, float suffixPx, int color){
        String mainStr = main==null ? "" : main.toString();
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTypeface(mainTf);
        p.setTextSize(mainPx);
        float mainW=p.measureText(mainStr);
        // Crop tight to the actual glyph bounds (not the full font line box) so there's
        // no dead space above/below the text.
        Rect ink=new Rect();
        if(mainStr.length()>0) p.getTextBounds(mainStr, 0, mainStr.length(), ink);

        Paint sp=null; float gap=0f, suffixW=0f;
        String suffixStr = suffix==null ? "" : suffix.toString();
        if(suffixStr.length()>0){
            sp=new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setColor(color);
            sp.setTypeface(suffixTf==null?Typeface.DEFAULT:suffixTf);
            sp.setTextSize(suffixPx);
            gap=mainPx*0.12f;
            suffixW=sp.measureText(suffixStr);
        }

        int pad=(int)Math.ceil(mainPx*0.03f+2f);
        int w=(int)Math.ceil(mainW+gap+suffixW)+pad*2;
        int h=(ink.bottom-ink.top)+pad*2;
        if(w<1) w=1;
        if(h<1) h=1;
        Bitmap bmp=Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(bmp);
        float baseline=pad-ink.top;
        cv.drawText(mainStr, pad, baseline, p);
        if(sp!=null){
            cv.drawText(suffixStr, pad+mainW+gap, baseline, sp);
        }
        bmp.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return bmp;
    }

    /**
     * Renders the next-alarm line: the Material Icons alarm glyph (tinted to {@code
     * color}, matching the wallpaper-tinted clock/date) followed by the time text,
     * vertically centered together in one bitmap.
     */
    static Bitmap renderAlarm(Context context, CharSequence timeText, Typeface tf, float textPx, int color){
        Bitmap iconBmp=renderText(context, ALARM_GLYPH, alarmIconTypeface(context), textPx, null, null, 0f, color);
        Bitmap textBmp=renderText(context, timeText, tf, textPx, null, null, 0f, color);
        int gap=Math.round(textPx*0.12f);
        int w=Math.max(1, iconBmp.getWidth()+gap+textBmp.getWidth());
        int h=Math.max(1, Math.max(iconBmp.getHeight(), textBmp.getHeight()));
        Bitmap out=Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(out);
        cv.drawBitmap(iconBmp, 0, (h-iconBmp.getHeight())/2f, null);
        cv.drawBitmap(textBmp, iconBmp.getWidth()+gap, (h-textBmp.getHeight())/2f, null);
        out.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return out;
    }

    /**
     * Renders the weather line: a tinted Material Icons weather glyph followed by the
     * temperature, vertically centered together (mirrors {@link #renderAlarm}).
     */
    private static Bitmap renderWeather(Context context, String glyph, String tempText, float textPx, int color){
        Bitmap iconBmp=renderText(context, glyph, weatherTypeface(context), textPx*1.05f, null, null, 0f, color);
        Bitmap textBmp=renderText(context, tempText, dateTypeface(context), textPx, null, null, 0f, color);
        int gap=Math.round(textPx*0.08f);
        int w=Math.max(1, iconBmp.getWidth()+gap+textBmp.getWidth());
        int h=Math.max(1, Math.max(iconBmp.getHeight(), textBmp.getHeight()));
        Bitmap out=Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(out);
        cv.drawBitmap(iconBmp, 0, (h-iconBmp.getHeight())/2f, null);
        cv.drawBitmap(textBmp, iconBmp.getWidth()+gap, (h-textBmp.getHeight())/2f, null);
        out.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return out;
    }

    /**
     * Renders the clock, date and alarm bitmaps into the given RemoteViews and sets
     * the alarm visibility. Returns whether the calendar should be hidden (used by
     * the full update path). Shared by full updates and per-minute ticks.
     */
    private static boolean renderClockDate(Context context, AppWidgetManager appWidgetManager, int appWidgetId, RemoteViews views, boolean sizeCalendar){
        boolean hideAlarm=false, hideCalendar=false;
        float clockFontScale=1f, dateFontScale=1f;
        Bundle options=appWidgetManager.getAppWidgetOptions(appWidgetId);
        if(options!=null){
            int h=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            int w=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            Clock31Logic.Sizing sz=Clock31Logic.sizingFor(w, h);
            clockFontScale=sz.clockScale;
            dateFontScale=sz.dateScale;
            hideAlarm=sz.hideAlarm;
            hideCalendar=sz.hideCalendar;
        }
        if(WidgetPrefs.clockOnly(context)) hideCalendar=true; // clock-only mode hides the calendar
        if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.N){ clockFontScale=clockFontScale*0.8f; }

        float density=context.getResources().getDisplayMetrics().density;
        boolean is24=DateFormat.is24HourFormat(context);
        long now=System.currentTimeMillis();
        int tone=WidgetPrefs.colorTone(context);
        int clockColor=Clock31Logic.chosenToneColor(tone,
                resolveColor(context, R.color.widget_clock_color), resolveColor(context, R.color.widget_neutral_color));
        int dateColor=clockColor;

        // Clock: digits + AM/PM (12h only) in the clock font.
        float clockPx=(is24?100f:75f)*clockFontScale*density;
        CharSequence timeText=DateFormat.format(Clock31Logic.clockFormatPattern(is24), now);
        CharSequence ampm=Clock31Logic.ampmVisible(is24)?DateFormat.format("a", now):null;
        Bitmap clockBmp=renderText(context, timeText, clockTypeface(context), clockPx,
                ampm, clockTypeface(context), clockPx*0.5f, clockColor);
        views.setImageViewBitmap(R.id.clock, clockBmp);

        // Date in MiSans, formatted like the lock screen (e.g. "Jul 15, Wed").
        float datePx=22f*dateFontScale*density;
        CharSequence dateText=DateFormat.format(Clock31Logic.DATE_FORMAT_PATTERN, now);
        Bitmap dateBmp=renderText(context, dateText, dateTypeface(context), datePx,
                null, null, 0f, dateColor);
        views.setImageViewBitmap(R.id.date, dateBmp);

        // Weather (temp + glyph) next to the date, when cached and there's room.
        Clock31Logic.WeatherData weather=WidgetPrefs.weatherEnabled(context)?WeatherRepository.getCached(context):null;
        if(weather!=null && !hideAlarm){
            views.setImageViewBitmap(R.id.weather, renderWeather(context,
                    Clock31Logic.weatherGlyph(weather.code), Clock31Logic.formatTemp(weather.temp), datePx, dateColor));
            views.setViewVisibility(R.id.weather, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.weather, View.GONE);
        }

        // Next alarm in MiSans, when there is one and there's room.
        if(!hideAlarm){
            AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo alarmClock=am.getNextAlarmClock();
            if(alarmClock!=null){
                CharSequence alarmText=DateFormat.format(Clock31Logic.alarmFormatPattern(is24), alarmClock.getTriggerTime());
                views.setImageViewBitmap(R.id.alarm, renderAlarm(context, alarmText, dateTypeface(context), datePx, dateColor));
                views.setViewVisibility(R.id.alarm, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.alarm, View.GONE);
            }
        } else {
            views.setViewVisibility(R.id.alarm, View.GONE);
        }

        // Size the calendar list to a whole number of blocks so no clipped partial
        // block shows when it isn't scrolled (the full list is still scrollable).
        if(sizeCalendar && options!=null && Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
            int widgetHdp=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            int blockPx=calendarBlockHeightPx>0 ? calendarBlockHeightPx : computeCalendarBlockHeightPx(context, density);
            int dividerPx=(int)Math.ceil(6f*density);
            if(widgetHdp>0 && blockPx>dividerPx){
                // OPTION_APPWIDGET_MAX_HEIGHT under-reports the real widget height on
                // some launchers (e.g. HyperOS), which left a block of empty space at
                // the bottom. Add a correction so whole blocks fill the actual space.
                float availablePx=widgetHdp*density - clockBmp.getHeight() - dateBmp.getHeight() + 16f*density;
                int n=Clock31Logic.blocksThatFit(availablePx, blockPx, dividerPx);
                views.setViewLayoutHeight(R.id.calendar, Clock31Logic.listHeightPx(n, blockPx, dividerPx), TypedValue.COMPLEX_UNIT_PX);
            }
        }
        return hideCalendar;
    }

    /** Schedules the next clock redraw at the top of the next minute. */
    private static void scheduleTick(Context context){
        try{
            AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent i=new Intent(context, C31Widget.class).setAction(ACTION_TICK);
            PendingIntent pi=PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
            long now=System.currentTimeMillis();
            long next=Clock31Logic.nextMinuteBoundary(now);
            am.set(AlarmManager.RTC, next, pi);
        }catch(Throwable t){
            Log.v(TAG, "Failed to schedule clock tick");
        }
    }

    /** Lightweight per-minute refresh: redraw the clock/date/alarm only, leaving the calendar list untouched. */
    private static void tickUpdate(Context context){
        try{
            AppWidgetManager appWidgetManager=AppWidgetManager.getInstance(context);
            int[] ids=appWidgetManager.getAppWidgetIds(new ComponentName(context, C31Widget.class));
            for(int id : ids){
                RemoteViews views=new RemoteViews(context.getPackageName(), R.layout.c31_widget);
                renderClockDate(context, appWidgetManager, id, views, false);
                appWidgetManager.partiallyUpdateAppWidget(id, views);
            }
        }catch(Throwable t){
            Log.e(TAG, "Tick update error: "+t);
        }
        scheduleTick(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.v(TAG,"Intent received: "+intent.getAction());
        if(ACTION_TICK.equals(intent.getAction())){
            tickUpdate(context);
            return;
        }
        if(updatePending){
            Log.v(TAG,"Update already pending");
            return;
        }
        updatePending=true;
        onUpdate(context,AppWidgetManager.getInstance(context),AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, C31Widget.class)));
    }

    /**
     * Returns an intent that opens the user's clock/alarm app, or null if none is
     * available. Uses the standard SHOW_ALARMS action so it resolves to whatever
     * clock app the user actually has (Google Clock, the AOSP/Xiaomi clock, ...)
     * instead of a hard-coded package that breaks if that app isn't installed.
     */
    static Intent clockAppIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent showAlarms = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (showAlarms.resolveActivity(pm) != null) return showAlarms;
        // Fallback: launch a known clock app directly if SHOW_ALARMS isn't handled.
        for (String pkg : new String[]{"com.google.android.deskclock", "com.android.deskclock"}) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) return launch;
        }
        return null;
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        try {
            Log.v(TAG, "updateAppWidget: " + appWidgetId);
            // Construct the RemoteViews object
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.c31_widget);
            boolean hideCalendar = renderClockDate(context, appWidgetManager, appWidgetId, views, true);
            if(WidgetPrefs.weatherEnabled(context)) WeatherRepository.refreshIfStale(context, WidgetPrefs.celsius(context));
            try {
                Intent clockIntent = clockAppIntent(context);
                if (clockIntent != null) {
                    PendingIntent openClockApp = PendingIntent.getActivity(context, 0, clockIntent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
                    views.setOnClickPendingIntent(R.id.clock, openClockApp);
                    views.setOnClickPendingIntent(R.id.alarm, openClockApp);
                }
            } catch (Throwable t) {
                Log.v(TAG, "Failed to register event handler for tapping clock (no app?)");
            }
            try {
                // Tapping the date opens the calendar app at today's date.
                Intent openCalendar = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(Clock31Logic.calendarTimeUri(System.currentTimeMillis())))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (openCalendar.resolveActivity(context.getPackageManager()) != null) {
                    PendingIntent pi = PendingIntent.getActivity(context, 2, openCalendar, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
                    views.setOnClickPendingIntent(R.id.date, pi);
                }
            } catch (Throwable t) {
                Log.v(TAG, "Failed to register event handler for tapping date (no calendar app?)");
            }
            if (!hideCalendar) {
                views.setViewVisibility(R.id.calendar_container, View.VISIBLE);
                views.setRemoteAdapter(R.id.calendar, new Intent(context, CalendarRemoteViewsService.class));
                Intent eventClickTemplate = new Intent(Intent.ACTION_VIEW);
                PendingIntent eventClickPendingIntent = PendingIntent.getActivity(context, 0, eventClickTemplate, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0));
                views.setPendingIntentTemplate(R.id.calendar, eventClickPendingIntent);
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.calendar);
            } else {
                views.setViewVisibility(R.id.calendar_container, View.GONE);
            }
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
            scheduleTick(context);
        }catch(Throwable t){
            Log.e(TAG,"Internal error: "+t);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.v(TAG,"onUpdate");
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // When the user deletes the widget, delete the preference associated with it.
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
        onUpdate(context,AppWidgetManager.getInstance(context),AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, C31Widget.class)));
    }

    @Override
    public void onDisabled(Context context) {
        // Last widget removed: stop the per-minute clock alarm.
        try{
            AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent i=new Intent(context, C31Widget.class).setAction(ACTION_TICK);
            PendingIntent pi=PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
            am.cancel(pi);
        }catch(Throwable t){
            Log.v(TAG, "Failed to cancel clock tick");
        }
    }

}
