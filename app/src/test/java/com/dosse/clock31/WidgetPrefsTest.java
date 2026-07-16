package com.dosse.clock31;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Guards the config-screen persistence: sensible defaults ("automatic, no settings") and
 * a round-trip through SharedPreferences.
 */
@RunWith(RobolectricTestRunner.class)
public class WidgetPrefsTest {

    @Test
    public void defaultsAreAutomatic() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(Clock31Logic.TONE_ACCENT, WidgetPrefs.colorTone(c));
        assertFalse(WidgetPrefs.clockOnly(c));
        assertTrue(WidgetPrefs.weatherEnabled(c));
        assertTrue(WidgetPrefs.celsius(c));
    }

    @Test
    public void savedValuesRoundTrip() {
        Context c = RuntimeEnvironment.getApplication();
        WidgetPrefs.save(c, Clock31Logic.TONE_WHITE, true, false, false);
        assertEquals(Clock31Logic.TONE_WHITE, WidgetPrefs.colorTone(c));
        assertTrue(WidgetPrefs.clockOnly(c));
        assertFalse(WidgetPrefs.weatherEnabled(c));
        assertFalse(WidgetPrefs.celsius(c));
    }
}
