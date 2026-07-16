package com.dosse.clock31;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import com.dosse.clock31.databinding.C31WidgetConfigureBinding;

/**
 * The configuration screen for the {@link C31Widget C31Widget} AppWidget.
 */
public class C31WidgetConfigureActivity extends Activity {

    private static final String PREFS_NAME = "com.dosse.clock31.C31Widget";
    private static final String PREF_PREFIX_KEY = "appwidget_";
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private C31WidgetConfigureBinding binding;

    public C31WidgetConfigureActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED);

        binding = C31WidgetConfigureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean calendar = checkPermission(Manifest.permission.READ_CALENDAR,Process.myPid(),Process.myUid())==PackageManager.PERMISSION_GRANTED;
            boolean location = checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION,Process.myPid(),Process.myUid())==PackageManager.PERMISSION_GRANTED;
            if(calendar && location){
                proceedWithWidgetCreation();
                return;
            } else if(calendar){
                // Calendar is granted; ask once for location so the weather line can work.
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},102);
            } else {
                // Ask for both; only calendar is required to create the widget.
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION},101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==101){
            // Calendar is required; location is optional (weather only).
            boolean calendarGranted=false;
            for(int i=0;i<permissions.length;i++){
                if(Manifest.permission.READ_CALENDAR.equals(permissions[i]) && grantResults[i]==PackageManager.PERMISSION_GRANTED){
                    calendarGranted=true;
                }
            }
            if(calendarGranted) proceedWithWidgetCreation(); else failWidgetCreation();
        } else if(requestCode==102){
            // Location-only (opportunistic); create the widget regardless of the choice.
            proceedWithWidgetCreation();
        }
    }

    private void proceedWithWidgetCreation(){
        // It is the responsibility of the configuration activity to update the app widget
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        C31Widget.updateAppWidget(this, appWidgetManager, mAppWidgetId);
        // Make sure we pass back the original appWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    private void failWidgetCreation(){
        // Make sure we pass back the original appWidgetId
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_CANCELED, resultValue);
        finish();
    }
}