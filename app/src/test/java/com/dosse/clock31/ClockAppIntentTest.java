package com.dosse.clock31;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPackageManager;

/**
 * Guards the "tap the clock opens the user's clock app" behaviour: it must resolve the
 * standard SHOW_ALARMS action when a clock app is present, and return null (no crash,
 * no handler registered) when nothing can handle it.
 */
@RunWith(RobolectricTestRunner.class)
public class ClockAppIntentTest {

    @Test
    public void prefersShowAlarms_whenAHandlerExists() {
        Application app = RuntimeEnvironment.getApplication();
        ShadowPackageManager spm = shadowOf(app.getPackageManager());
        ResolveInfo ri = new ResolveInfo();
        ri.activityInfo = new ActivityInfo();
        ri.activityInfo.packageName = "com.example.clock";
        ri.activityInfo.name = "com.example.clock.AlarmsActivity";
        spm.addResolveInfoForIntent(new Intent(AlarmClock.ACTION_SHOW_ALARMS), ri);

        Intent got = C31Widget.clockAppIntent(app);
        assertNotNull(got);
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, got.getAction());
    }

    @Test
    public void returnsNull_whenNoClockAppAtAll() {
        Application app = RuntimeEnvironment.getApplication();
        assertNull(C31Widget.clockAppIntent(app));
    }
}
