package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.provider.Settings;

public class MobileDataButton extends PowerButton {

    public static final String MOBILE_DATA_CHANGED = "com.android.internal.telephony.MOBILE_DATA_CHANGED";

    public static boolean STATE_CHANGE_REQUEST = false;
    private static MobileDataButton OWN_BUTTON = null;

    public MobileDataButton() { mType = BUTTON_MOBILEDATA; }

    @Override
    protected void updateState() {
        if (STATE_CHANGE_REQUEST) {
            mIcon = R.drawable.stat_data_on;
            mState = STATE_INTERMEDIATE;
        } else  if (getDataState(mView.getContext())) {
            mIcon = R.drawable.stat_data_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_data_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        boolean enabled = getDataState(context);

        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (enabled) {
            cm.setMobileDataEnabled(false);
        } else {
            cm.setMobileDataEnabled(true);
        }
    }

    public static MobileDataButton getInstance() {
        if (OWN_BUTTON == null) OWN_BUTTON = new MobileDataButton();
        return OWN_BUTTON;
    }

    private static boolean getDataRomingEnabled(Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.DATA_ROAMING,0) > 0;
    }

    private static boolean getDataState(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getMobileDataEnabled();
    }

    public void networkModeChanged(Context context, int networkMode) {
        if (STATE_CHANGE_REQUEST) {
            ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.setMobileDataEnabled(true);
            STATE_CHANGE_REQUEST=false;
        }
    }

}
