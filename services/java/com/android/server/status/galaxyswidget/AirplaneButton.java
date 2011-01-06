package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class AirplaneButton extends PowerButton {

    private static AirplaneButton OWN_BUTTON = null;

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.AIRPLANE_MODE_ON));
    }

    public AirplaneButton() { mType = PowerButton.BUTTON_AIRPLANE; }

    @Override
    public void updateState() {
        if (getState(mView.getContext())) {
            mIcon = R.drawable.stat_airplane_on;
            mState = PowerButton.STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_airplane_off;
            mState = PowerButton.STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        boolean state = getState(context);
        Settings.System.putInt(context.getContentResolver(),
            Settings.System.AIRPLANE_MODE_ON, state ? 0 : 1);
        // notify change
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.putExtra("state", state);
        context.sendBroadcast(intent);
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    public static AirplaneButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new AirplaneButton();
        return OWN_BUTTON;
    }

    private static boolean getState(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                 Settings.System.AIRPLANE_MODE_ON,0) == 1;
    }
}

