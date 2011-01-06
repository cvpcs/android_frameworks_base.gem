package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.os.PowerManager;
import android.os.SystemClock;

public class SleepButton extends PowerButton {
    private static SleepButton OWN_BUTTON = null;

    public SleepButton() { mType = BUTTON_SLEEP; }

    @Override
    public void updateState() {
        mIcon = com.android.internal.R.drawable.stat_sleep;
        mState = STATE_DISABLED;
    }

    @Override
    protected void toggleState() {
        PowerManager pm = (PowerManager)mView.getContext()
                .getSystemService(Context.POWER_SERVICE);
        pm.goToSleep(SystemClock.uptimeMillis() + 1);
    }

    public static SleepButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new SleepButton();
        return OWN_BUTTON;
    }
}
