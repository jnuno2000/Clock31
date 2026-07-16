package com.dosse.clock31;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Guards the Material You color feature: the widget's clock color must come from the
 * plain-white fallback below API 31 and from the wallpaper accent (values-v31) on 31+.
 */
@RunWith(RobolectricTestRunner.class)
public class ColorResolutionTest {

    @Test
    @Config(sdk = 30)
    public void belowApi31_clockIsWhite() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals(0xFFFFFFFF, C31Widget.resolveColor(c, R.color.widget_clock_color));
    }

    @Test
    @Config(sdk = 31)
    public void api31_clockUsesWallpaperAccent() {
        Context c = RuntimeEnvironment.getApplication();
        int color = C31Widget.resolveColor(c, R.color.widget_clock_color);
        assertEquals("accent color should be fully opaque", 0xFF, (color >>> 24));
        assertNotEquals("values-v31 accent should not be plain white", 0xFFFFFFFF, color);
    }
}
