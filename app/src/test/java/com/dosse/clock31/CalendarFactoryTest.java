package com.dosse.clock31;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.RemoteViewsService.RemoteViewsFactory;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowContentResolver;

/**
 * Guards the calendar data path: the factory must read the CalendarProvider, expose one
 * row per event with stable event ids, and fall back to a single empty-state row (id -1)
 * when there are no events.
 */
@RunWith(RobolectricTestRunner.class)
public class CalendarFactoryTest {

    private static final String[] COLS = new String[]{
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_COLOR
    };

    private void registerCursor(Cursor cursor) {
        ShadowContentResolver.registerProviderInternally(CalendarContract.AUTHORITY, new FakeProvider(cursor));
    }

    private RemoteViewsFactory factoryAfterLoad() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR);
        CalendarRemoteViewsService svc = Robolectric.setupService(CalendarRemoteViewsService.class);
        RemoteViewsFactory f = svc.onGetViewFactory(new Intent());
        f.onCreate();
        return f;
    }

    @Test
    public void countsEventsAndExposesStableIds() {
        MatrixCursor c = new MatrixCursor(COLS);
        long now = System.currentTimeMillis();
        c.addRow(new Object[]{101L, "Standup", now + 3600000L, now + 7200000L, 0, 0xFF4285F4});
        c.addRow(new Object[]{102L, "Lunch", now + 8000000L, now + 9000000L, 0, 0xFF34A853});
        registerCursor(c);

        RemoteViewsFactory f = factoryAfterLoad();
        assertEquals(2, f.getCount());
        assertEquals(101L, f.getItemId(0));
        assertEquals(102L, f.getItemId(1));
    }

    @Test
    public void emptyState_hasOneRowWithNoId() {
        registerCursor(new MatrixCursor(COLS));
        RemoteViewsFactory f = factoryAfterLoad();
        assertEquals(1, f.getCount());
        assertEquals(-1L, f.getItemId(0));
    }

    /** Minimal provider that returns a fixed cursor for any calendar query. */
    private static class FakeProvider extends ContentProvider {
        private final Cursor cursor;
        FakeProvider(Cursor cursor) { this.cursor = cursor; }
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return cursor; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    }
}
