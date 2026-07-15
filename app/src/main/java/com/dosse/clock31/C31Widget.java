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
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.text.format.DateFormat;

/**
 * Implementation of App Widget functionality.
 * App Widget Configuration implemented in {@link C31WidgetConfigureActivity C31WidgetConfigureActivity}
 *
 * The clock, date and alarm are drawn to bitmaps using the bundled custom fonts
 * (SF Rail Time for the clock, MiSans for the date/alarm), because home-screen
 * widgets can't reliably apply custom fonts to text views. The clock bitmap is
 * refreshed once a minute via {@link #ACTION_TICK}.
 */
public class C31Widget extends AppWidgetProvider {

    private static final String TAG="C31WidgetProvider";

    public static boolean updatePending =false;

    // How many whole calendar blocks fit in the current widget height. Computed by
    // the provider (which knows the widget size and the clock/date bitmap heights)
    // and read by CalendarRemoteViewsService.getCount() so the list never renders a
    // clipped partial block. Shared via static because both run in the same process.
    public static volatile int maxCalendarBlocks = 3;

    public static final String ACTION_REFRESH="com.dosse.clock31.ACTION_REFRESH";
    public static final String ACTION_TICK="com.dosse.clock31.ACTION_TICK";

    private static Typeface clockTypeface, dateTypeface;

    private static Typeface clockTypeface(Context context){
        if(clockTypeface==null){
            try{ clockTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/mi_sans_light.ttf"); }
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

    private static int resolveColor(Context context, int resId){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M) return context.getColor(resId);
        return context.getResources().getColor(resId);
    }

    /**
     * Renders text to a bitmap using the given typeface, with an optional smaller
     * suffix (e.g. AM/PM) in a second typeface baseline-aligned next to it, plus a
     * soft drop shadow so light text stays legible over any wallpaper.
     */
    private static Bitmap renderText(Context context, CharSequence main, Typeface mainTf, float mainPx,
                                     CharSequence suffix, Typeface suffixTf, float suffixPx, int color){
        String mainStr = main==null ? "" : main.toString();
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTypeface(mainTf);
        p.setTextSize(mainPx);
        Paint.FontMetrics fm=p.getFontMetrics();
        float mainW=p.measureText(mainStr);

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
        int h=(int)Math.ceil(fm.descent-fm.ascent)+pad*2;
        if(w<1) w=1;
        if(h<1) h=1;
        Bitmap bmp=Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(bmp);
        float baseline=pad-fm.ascent;
        cv.drawText(mainStr, pad, baseline, p);
        if(sp!=null){
            cv.drawText(suffixStr, pad+mainW+gap, baseline, sp);
        }
        bmp.setDensity(context.getResources().getDisplayMetrics().densityDpi);
        return bmp;
    }

    /**
     * Renders the clock, date and alarm bitmaps into the given RemoteViews and sets
     * the alarm visibility. Returns whether the calendar should be hidden (used by
     * the full update path). Shared by full updates and per-minute ticks.
     */
    private static boolean renderClockDate(Context context, AppWidgetManager appWidgetManager, int appWidgetId, RemoteViews views){
        boolean hideAlarm=false, hideCalendar=false;
        float clockFontScale=1f, dateFontScale=1f;
        Bundle options=appWidgetManager.getAppWidgetOptions(appWidgetId);
        if(options!=null){
            int h=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            int w=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            clockFontScale=Math.max(0.4f, Math.min(1, Math.min(h, w) / 275f));
            dateFontScale=Math.max(0.7f, Math.min(1, Math.min(h, w) / 275f));
            if(w<220){ hideAlarm=true; dateFontScale=Math.min(1, dateFontScale*1.25f); }
            if(h<80){ hideCalendar=true; clockFontScale=Math.min(1, clockFontScale*1.5f); dateFontScale=Math.min(1, dateFontScale*1.25f); }
        }
        if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.N){ clockFontScale=clockFontScale*0.8f; }

        float density=context.getResources().getDisplayMetrics().density;
        boolean is24=DateFormat.is24HourFormat(context);
        long now=System.currentTimeMillis();
        int clockColor=resolveColor(context, R.color.widget_clock_color);
        int dateColor=resolveColor(context, R.color.widget_date_color);

        // Clock: digits + AM/PM (12h only) in the clock font.
        float clockPx=(is24?80f:60f)*clockFontScale*density;
        CharSequence timeText=DateFormat.format(is24?"HH:mm":"h:mm", now);
        CharSequence ampm=is24?null:DateFormat.format("a", now);
        Bitmap clockBmp=renderText(context, timeText, clockTypeface(context), clockPx,
                ampm, clockTypeface(context), clockPx*0.5f, clockColor);
        views.setImageViewBitmap(R.id.clock, clockBmp);

        // Date in MiSans, formatted like the lock screen (e.g. "Jul 15, Wed").
        float datePx=18f*dateFontScale*density;
        CharSequence dateText=DateFormat.format("MMM d, EEE", now);
        Bitmap dateBmp=renderText(context, dateText, dateTypeface(context), datePx,
                null, null, 0f, dateColor);
        views.setImageViewBitmap(R.id.date, dateBmp);

        // Cap the calendar to whole blocks that fit under the clock/date, so the list
        // never shows a clipped partial block. Uses the real bitmap heights + widget
        // height; biased slightly conservative (a block that only half-fits is dropped).
        if(options!=null){
            int widgetHdp=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            if(widgetHdp>0){
                float availablePx=widgetHdp*density - clockBmp.getHeight() - dateBmp.getHeight() - 20f*density;
                float blockPx=58f*density; // event block incl. gap (approx), slightly over to avoid slivers
                maxCalendarBlocks=Math.max(1, (int)Math.floor(availablePx/blockPx));
            }
        }

        // Next alarm in MiSans, when there is one and there's room.
        if(!hideAlarm){
            AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo alarmClock=am.getNextAlarmClock();
            if(alarmClock!=null){
                CharSequence alarmText=DateFormat.format(is24?"⏰ E HH:mm":"⏰ E h:mm a", alarmClock.getTriggerTime());
                views.setImageViewBitmap(R.id.alarm, renderText(context, alarmText, dateTypeface(context), datePx,
                        null, null, 0f, dateColor));
                views.setViewVisibility(R.id.alarm, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.alarm, View.GONE);
            }
        } else {
            views.setViewVisibility(R.id.alarm, View.GONE);
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
            long next=now - (now % 60000L) + 60000L;
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
                renderClockDate(context, appWidgetManager, id, views);
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

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        try {
            Log.v(TAG, "updateAppWidget: " + appWidgetId);
            // Construct the RemoteViews object
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.c31_widget);
            boolean hideCalendar = renderClockDate(context, appWidgetManager, appWidgetId, views);
            PackageManager pm = context.getPackageManager();
            try {
                PendingIntent openClockApp = PendingIntent.getActivity(context, 0, pm.getLaunchIntentForPackage("com.android.deskclock"), PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
                views.setOnClickPendingIntent(R.id.clock, openClockApp);
                views.setOnClickPendingIntent(R.id.alarm, openClockApp);
            } catch (Throwable t) {
                Log.v(TAG, "Failed to register event handler for tapping clock (no app?)");
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
