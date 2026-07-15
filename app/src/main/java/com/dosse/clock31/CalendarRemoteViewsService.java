package com.dosse.clock31;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
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

    /**
     * Returns black or white (at the given alpha) depending on which contrasts
     * better with the given background color, so event text stays readable on
     * both light and dark calendar colors.
     */
    private static int contrastColor(int bg, int alpha){
        int r=(bg>>16)&0xff, g=(bg>>8)&0xff, b=bg&0xff;
        double lum=(0.299*r+0.587*g+0.114*b)/255.0;
        int base = lum>0.6 ? 0x000000 : 0xffffff;
        return (alpha<<24) | (base & 0xffffff);
    }

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
                v.setTextViewText(R.id.event_title,data.eventTitle);
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
                v.setTextViewText(R.id.event_date,formattedDate);
                int blockColor = (data.eventColor >>> 24) == 0 ? DEFAULT_EVENT_COLOR : data.eventColor;
                v.setViewVisibility(R.id.block_bg, View.VISIBLE);
                v.setInt(R.id.block_bg, "setColorFilter", blockColor);
                v.setTextColor(R.id.event_title, contrastColor(blockColor, 0xff));
                v.setTextColor(R.id.event_date, contrastColor(blockColor, 0xcc));
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
                v.setTextViewText(R.id.event_title,getString(R.string.no_events));
                v.setTextViewText(R.id.event_date,getString(R.string.tap_calendar));
                v.setViewVisibility(R.id.block_bg, View.INVISIBLE);
                v.setTextColor(R.id.event_title, 0xffffffff);
                v.setTextColor(R.id.event_date, 0xffffffff);
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
