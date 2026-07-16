package com.dosse.clock31;

import static org.junit.Assert.assertNull;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Guards the "tap the clock opens the user's clock app" behaviour: when nothing on the
 * device can handle the alarm/clock intent, the widget must degrade gracefully to null
 * (no crash, no click handler) rather than the old hard-coded package that broke when
 * that package wasn't installed.
 *
 * The positive path (SHOW_ALARMS resolves to the installed clock app) isn't unit-tested
 * here because registering an intent resolver requires ShadowPackageManager, which can't
 * be referenced when compiling against this compileSdk (a Robolectric/SDK constraint);
 * that path is exercised on-device.
 */
@RunWith(RobolectricTestRunner.class)
public class ClockAppIntentTest {

    @Test
    public void returnsNull_whenNoClockAppAtAll() {
        Application app = RuntimeEnvironment.getApplication();
        assertNull(C31Widget.clockAppIntent(app));
    }
}
