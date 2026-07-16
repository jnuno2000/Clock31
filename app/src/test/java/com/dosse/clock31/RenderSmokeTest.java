package com.dosse.clock31;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Smoke coverage for the bitmap render paths (clock/date/alarm are drawn to bitmaps
 * because widgets can't apply custom fonts to text views). Pixel geometry can't be
 * asserted on the JVM, but this catches crashes/regressions that stop a bitmap being
 * produced at all.
 */
@RunWith(RobolectricTestRunner.class)
public class RenderSmokeTest {

    @Test
    public void renderAlarm_producesBitmap() {
        Context c = RuntimeEnvironment.getApplication();
        Bitmap bmp = C31Widget.renderAlarm(c, "Thu 07:00", Typeface.DEFAULT, 44f, 0xFFFFFFFF);
        assertNotNull(bmp);
        assertTrue(bmp.getWidth() > 0);
        assertTrue(bmp.getHeight() > 0);
    }

    @Test
    public void renderText_producesBitmap() {
        Context c = RuntimeEnvironment.getApplication();
        Bitmap bmp = C31Widget.renderText(c, "12:34", Typeface.DEFAULT, 80f, null, null, 0f, 0xFFFFFFFF);
        assertNotNull(bmp);
        assertTrue(bmp.getWidth() > 0);
        assertTrue(bmp.getHeight() > 0);
    }
}
