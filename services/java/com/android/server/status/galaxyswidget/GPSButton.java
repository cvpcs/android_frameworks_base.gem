package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.ContentResolver;
import android.content.Context;
import android.location.LocationManager;
import android.provider.Settings;

public class GPSButton extends PowerButton {

    private static GPSButton OWN_BUTTON = null;

    public GPSButton() { mType = BUTTON_GPS; }

    @Override
    public void updateState() {
        if(getGpsState(mView.getContext())) {
            mIcon = com.android.internal.R.drawable.stat_gps_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = com.android.internal.R.drawable.stat_gps_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        ContentResolver resolver = context.getContentResolver();
        boolean enabled = getGpsState(context);
        Settings.Secure.setLocationProviderEnabled(resolver,
                LocationManager.GPS_PROVIDER, !enabled);
    }

    public static GPSButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new GPSButton();
        return OWN_BUTTON;
    }

    private static boolean getGpsState(Context context) {
        ContentResolver resolver = context.getContentResolver();
        return Settings.Secure.isLocationProviderEnabled(resolver,
                LocationManager.GPS_PROVIDER);
    }
}
