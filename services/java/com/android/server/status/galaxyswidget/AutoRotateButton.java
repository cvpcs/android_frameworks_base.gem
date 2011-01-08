package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

public class AutoRotateButton extends PowerButton {

    private static AutoRotateButton OWN_BUTTON = null;

    private static final List<Uri> OBSERVED_URIS = new ArrayList<Uri>();
    static {
        OBSERVED_URIS.add(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION));
    }

    public AutoRotateButton() { mType = BUTTON_AUTOROTATE; }

    @Override
    protected void updateState() {
        if (getOrientationState(mView.getContext()) == 1) {
            mIcon = R.drawable.stat_orientation_on;
            mState = STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_orientation_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        if(getOrientationState(context) == 0) {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 1);
        } else {
            Settings.System.putInt(
                    context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, 0);
        }
    }

    @Override
    protected List<Uri> getObservedUris() {
        return OBSERVED_URIS;
    }

    public static AutoRotateButton getInstance() {
        if (OWN_BUTTON == null) OWN_BUTTON = new AutoRotateButton();
        return OWN_BUTTON;
    }

    private static int getOrientationState(Context context) {
        return Settings.System.getInt(
                context.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 0);
    }
}
