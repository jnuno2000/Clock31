package com.dosse.clock31;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CalendarRemoteViewsService extends RemoteViewsService{

    private static final String TAG="CalendarSvc";

    // Fallback fill used when an event has no calendar color.
    private static final int DEFAULT_EVENT_COLOR = 0xFF757575;

    public CalendarRemoteViewsService() {
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CalendarRemoteViewsFactory(getApplicationContext());
    }

    public class CalendarRemoteViewsFactory implements RemoteViewsFactory{
        private Context context;

        private class CalendarListEntry {
            public final long eventId;
            public final String eventTitle;
            public final long eventBegin;
            public final long eventEnd;
            public final boolean eventAllDay;
            public final int eventColor;

            public CalendarListEntry(long eventId, String eventTitle, long eventBegin, long eventEnd, boolean eventAllDay, int eventColor) {
                if (eventAllDay) { //for some reason android stores all day events in UTC time instead of local... sigh
                    eventBegin = utcToLocal(eventBegin);
                    eventEnd = utcToLocal(eventEnd);
                }
                this.eventId = eventId;
                this.eventTitle = eventTitle;
                this.eventBegin = eventBegin;
                this.eventEnd = eventEnd;
                this.eventAllDay = eventAllDay;
                this.eventColor = eventColor;
            }

            private long utcToLocal(long utc){
                Time t=new Time();
                t.timezone=Time.TIMEZONE_UTC;
                t.set(utc);
                t.timezone=Time.getCurrentTimezone();
                return t.normalize(true);
            }
        }

        private List<CalendarListEntry> entries=new ArrayList<>();

        public CalendarRemoteViewsFactory(Context context) {
            this.context = context;
        }

        private Typeface titleTypeface, timeTypeface;

        private Typeface titleTypeface(){
            if(titleTypeface==null){
                try{ titleTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/google_sans_medium.ttf"); }
                catch(Throwable t){ titleTypeface=Typeface.DEFAULT; }
            }
            return titleTypeface;
        }

        private Typeface timeTypeface(){
            if(timeTypeface==null){
                try{ timeTypeface=Typeface.createFromAsset(context.getAssets(),"fonts/google_sans_regular.ttf"); }
                catch(Throwable t){ timeTypeface=Typeface.DEFAULT; }
            }
            return timeTypeface;
        }

        /** Renders one line of event text (in Google Sans) to a bitmap, ellipsized to the block width. */
        private Bitmap renderEventText(CharSequence text, Typeface tf, float sizePx, int color, float maxWidthPx){
            TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(color);
            tp.setTypeface(tf);
            tp.setTextSize(sizePx);
            CharSequence shown=TextUtils.ellipsize(text==null?"":text, tp, Math.max(1f, maxWidthPx), TextUtils.TruncateAt.END);
            String s=shown.toString();
            Paint.FontMetrics fm=tp.getFontMetrics();
            float w=tp.measureText(s);
            int pad=Math.max(2,(int)Math.ceil(sizePx*0.06f));
            int bw=(int)Math.ceil(w)+pad*2;
            int bh=(int)Math.ceil(fm.descent-fm.ascent)+pad*2;
            if(bw<1) bw=1;
            if(bh<1) bh=1;
            Bitmap bmp=Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas cv=new Canvas(bmp);
            cv.drawText(s, pad, pad-fm.ascent, tp);
            bmp.setDensity(context.getResources().getDisplayMetrics().densityDpi);
            return bmp;
        }

        /** Approximate width available for event text inside a block. */
        private float eventTextMaxWidth(){
            float density=context.getResources().getDisplayMetrics().density;
            float w=context.getResources().getDisplayMetrics().widthPixels - 60f*density;
            return Math.max(1f, w);
        }

        private static final long HOUR_IN_MS=60*60*1000;
        private static final long DAY_IN_MS=24*HOUR_IN_MS;

        private void updateCalendarInfo(){
            Log.v(TAG,"Updating calendar info");
            C31Widget.updatePending=false;
            if(context.checkPermission(Manifest.permission.READ_CALENDAR, Process.myPid(), Process.myUid())== PackageManager.PERMISSION_GRANTED){
                try {
                    Uri uri = Uri.withAppendedPath(CalendarContract.Instances.CONTENT_URI, String.format(Locale.ENGLISH, "%d/%d", System.currentTimeMillis(), System.currentTimeMillis() + 14 * DAY_IN_MS));
                    Cursor cursor = context.getContentResolver().query(uri, new String[]{
                            CalendarContract.Instances.EVENT_ID,
                            CalendarContract.Events.TITLE,
                            CalendarContract.Instances.BEGIN,
                            CalendarContract.Instances.END,
                            CalendarContract.Events.ALL_DAY,
                            CalendarContract.Events.CALENDAR_COLOR
                    }, "", null, CalendarContract.Instances.BEGIN + " ASC");
                    entries.clear();
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            CalendarListEntry entry = new CalendarListEntry(
                                    cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)),
                                    cursor.getString(cursor.getColumnIndex(CalendarContract.Instances.TITLE)),
                                    cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.BEGIN)),
                                    cursor.getLong(cursor.getColumnIndex(CalendarContract.Instances.END)),
                                    cursor.getInt(cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)) != 0,
                                    cursor.getInt(cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR))
                            );
                            entries.add(entry);
                        }
                        cursor.close();
                    }
                    Log.v(TAG, "Calendar has " + entries.size() + " upcoming events");
                }catch(Throwable t){
                    Log.v(TAG,"Error updating calendar");
                    entries.clear();
                }
            }else{
                Log.v(TAG,"Not allowed to read calendar");
                entries.clear();
            }
            AlarmManager am=(AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
            Intent refreshIntent = new Intent(context, C31Widget.class);
            refreshIntent.setAction(C31Widget.ACTION_REFRESH);
            PendingIntent pi=PendingIntent.getBroadcast(context,0,refreshIntent,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M?PendingIntent.FLAG_IMMUTABLE:0));
            long now=System.currentTimeMillis(), refreshAt=now+HOUR_IN_MS/*DAY_IN_MS-now%DAY_IN_MS*/;
            for(CalendarListEntry e:entries){
                if(e.eventBegin>=now&&e.eventBegin<=refreshAt){
                    refreshAt=e.eventBegin;
                }
                if(e.eventEnd>=now&&e.eventEnd<=refreshAt){
                    refreshAt=e.eventEnd;
                }
            }
            am.set(AlarmManager.RTC,refreshAt,pi);
            Log.v(TAG,"Auto refresh calendar at "+ DateFormat.getTimeFormat(context).format(refreshAt));
        }

        @Override
        public void onCreate() {
            updateCalendarInfo();
        }

        @Override
        public void onDataSetChanged() {
            updateCalendarInfo();
        }

        public void onDestroy(){
            entries.clear(); //TODO: is this really necessary?
        }

        @Override
        public int getCount() {
            if(!entries.isEmpty()) return entries.size(); else return 1;
        }

        @Override
        public RemoteViews getViewAt(int i) {
            if(!entries.isEmpty()){
                RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.calendar_entry);
                CalendarListEntry data=entries.get(i);
                if(data==null) return null;
                float density=context.getResources().getDisplayMetrics().density;
                float maxW=eventTextMaxWidth();
                Bitmap titleBmp=renderEventText(data.eventTitle, titleTypeface(), 15f*density, 0xffffffff, maxW);
                v.setImageViewBitmap(R.id.event_title, titleBmp);
                String formattedDate;
                if(data.eventAllDay){
                    if(data.eventEnd-data.eventBegin>DAY_IN_MS){
                        formattedDate= DateUtils.formatDateRange(context,data.eventBegin,data.eventEnd,DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_SHOW_WEEKDAY|DateUtils.FORMAT_ABBREV_ALL);
                    }else{
                        formattedDate= DateUtils.formatDateTime(context,data.eventBegin,DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_SHOW_WEEKDAY|DateUtils.FORMAT_ABBREV_ALL);
                    }
                }else{
                    if(DateUtils.isToday(data.eventBegin)&&DateUtils.isToday(data.eventEnd)){
                        formattedDate= DateUtils.formatDateRange(context,data.eventBegin,data.eventEnd,DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_NO_NOON|DateUtils.FORMAT_NO_MIDNIGHT);
                    }else{
                        formattedDate= DateUtils.formatDateRange(context,data.eventBegin,data.eventEnd,DateUtils.FORMAT_SHOW_DATE|DateUtils.FORMAT_SHOW_WEEKDAY|DateUtils.FORMAT_ABBREV_ALL|DateUtils.FORMAT_SHOW_TIME|DateUtils.FORMAT_NO_NOON|DateUtils.FORMAT_NO_MIDNIGHT);
                    }
                }
                Bitmap timeBmp=renderEventText(formattedDate, timeTypeface(), 12f*density, 0xe6ffffff, maxW);
                v.setImageViewBitmap(R.id.event_date, timeBmp);
                // Report the exact block height (title + time bitmaps + padding 9+9,
                // title/time margin 1, divider 6) so the provider can size the list
                // to whole blocks. Widget layout values must match calendar_entry.xml.
                C31Widget.calendarBlockHeightPx = titleBmp.getHeight() + timeBmp.getHeight()
                        + (int)Math.ceil((9f+9f+1f+6f)*density);
                int blockColor = (data.eventColor >>> 24) == 0 ? DEFAULT_EVENT_COLOR : data.eventColor;
                v.setViewVisibility(R.id.block_bg, View.VISIBLE);
                v.setInt(R.id.block_bg, "setColorFilter", blockColor);
                Intent openEvent=new Intent();
                openEvent.setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,data.eventId));
                openEvent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_HISTORY
                        | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                v.setOnClickFillInIntent(R.id.calendar_entry,openEvent);
                return v;
            }else{
                RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.calendar_entry);
                float density=context.getResources().getDisplayMetrics().density;
                float maxW=eventTextMaxWidth();
                v.setImageViewBitmap(R.id.event_title, renderEventText(getString(R.string.no_events), titleTypeface(), 15f*density, 0xffffffff, maxW));
                v.setImageViewBitmap(R.id.event_date, renderEventText(getString(R.string.tap_calendar), timeTypeface(), 12f*density, 0xe6ffffff, maxW));
                v.setViewVisibility(R.id.block_bg, View.INVISIBLE);
                return v;
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int i) {
            if(!entries.isEmpty()) return entries.get(i).eventId; else return -1;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }


}
