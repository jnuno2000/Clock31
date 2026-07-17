package com.dosse.clock31;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
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

    /** Registers a fake CalendarProvider that returns {@code cursor} for any query. */
    private void registerCursor(Cursor cursor) {
        FakeCalendarProvider.nextCursor = cursor;
        ProviderInfo info = new ProviderInfo();
        info.authority = CalendarContract.AUTHORITY;
        Robolectric.buildContentProvider(FakeCalendarProvider.class).create(info);
    }

    private RemoteViewsFactory factoryAfterLoad() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR);
        CalendarRemoteViewsService svc = Robolectric.setupService(CalendarRemoteViewsService.class);
        RemoteViewsFactory f = svc.onGetViewFactory(new Intent());
        f.onCreate();
        return f;
    }

    @Test
    public void listsEventsWithStableIds() {
        MatrixCursor c = new MatrixCursor(COLS);
        long now = System.currentTimeMillis();
        c.addRow(new Object[]{101L, "Standup", now + 3600000L, now + 7200000L, 0, 0xFF4285F4});
        c.addRow(new Object[]{102L, "Lunch", now + 8000000L, now + 9000000L, 0, 0xFF34A853});
        registerCursor(c);

        RemoteViewsFactory f = factoryAfterLoad();
        int n = f.getCount();
        assertTrue("rows should include at least the two events (plus day headers)", n >= 2);
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) ids.add(f.getItemId(i));
        assertTrue("event 101 present", ids.contains(101L));
        assertTrue("event 102 present", ids.contains(102L));
    }

    @Test
    public void emptyState_hasOneRowWithNoId() {
        registerCursor(new MatrixCursor(COLS));
        RemoteViewsFactory f = factoryAfterLoad();
        assertEquals(1, f.getCount());
        assertEquals(-1L, f.getItemId(0));
    }

    /**
     * Events across two days produce at least two day headers, and every row (headers
     * and events, incl. the tappable header wiring and the time-only event line) renders
     * without throwing.
     */
    @Test
    public void headersAndEventRows_renderWithoutThrowing() {
        MatrixCursor c = new MatrixCursor(COLS);
        long now = System.currentTimeMillis();
        long day = 24L * 3600000L;
        c.addRow(new Object[]{201L, "Today event", now + 3600000L, now + 7200000L, 0, 0xFF4285F4});
        c.addRow(new Object[]{202L, "Tomorrow event", now + day + 3600000L, now + day + 7200000L, 0, 0xFF34A853});
        registerCursor(c);

        RemoteViewsFactory f = factoryAfterLoad();
        int n = f.getCount();
        int headers = 0;
        for (int i = 0; i < n; i++) {
            assertNotNull("row " + i + " should render", f.getViewAt(i));
            if (f.getItemId(i) <= Long.MIN_VALUE + 1_000_000L) headers++; // header ids are MIN_VALUE + day
        }
        assertTrue("two distinct days should yield two headers", headers >= 2);
    }

    /** Minimal provider that returns a preset cursor for any calendar query. */
    public static class FakeCalendarProvider extends ContentProvider {
        static Cursor nextCursor;
        private Cursor cursor;
        @Override public boolean onCreate() { cursor = nextCursor; return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return cursor; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    }
}
