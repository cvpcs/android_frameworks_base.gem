package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Toast;

public class ScreenTimeoutButton extends PowerButton {

    // low and high thresholds for screen timeout
    private static final int SCREEN_TIMEOUT_LT = 30000;
    private static final int SCREEN_TIMEOUT_HT = 120000;
    private static final int[] SCREEN_TIMEOUTS = new int[] {
            15000,    // 15s
            30000,    // 30s
            60000,    //  1m
            120000,   //  2m
            600000,   // 10m
            1800000}; // 30m

    private static ScreenTimeoutButton OWN_BUTTON = null;

    public ScreenTimeoutButton() { mType = PowerButton.BUTTON_SCREENTIMEOUT; }

    @Override
    public void updateState() {
        int timeout=getScreenTtimeout(mView.getContext());

        if (timeout <= SCREEN_TIMEOUT_LT) {
            mIcon = R.drawable.stat_screen_timeout_off;
            mState = PowerButton.STATE_DISABLED;
        } else if (timeout <= SCREEN_TIMEOUT_HT) {
            mIcon = R.drawable.stat_screen_timeout_off;
            mState = PowerButton.STATE_INTERMEDIATE;
        } else {
            mIcon = R.drawable.stat_screen_timeout_on;
            mState = PowerButton.STATE_ENABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        int screentimeout = getScreenTtimeout(context);
        int newtimeout = -1;

        // cycle through the timeouts and set the new one in a cycle
        for(int timeout : SCREEN_TIMEOUTS) {
            // is this timeout greater than the last?
            if(screentimeout < timeout) {
                newtimeout = timeout;
                break;
            }
        }

        // if we didn't find a strictly greater timeout, it means we're cycling
        if (newtimeout < 0) {
            newtimeout = SCREEN_TIMEOUTS[0];
        }

        Settings.System.putInt(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, newtimeout);

        // inform users of how long the timeout is now
        Toast msg = Toast.makeText(context,
                "Screen timeout set to: " + timeoutToString(newtimeout),
                Toast.LENGTH_LONG);
        msg.setGravity(Gravity.CENTER, msg.getXOffset() / 2, msg.getYOffset() / 2);
        msg.show();
    }

    public static ScreenTimeoutButton getInstance() {
        if (OWN_BUTTON == null) OWN_BUTTON = new ScreenTimeoutButton();
        return OWN_BUTTON;
    }

    private static int getScreenTtimeout(Context context) {
        return Settings.System.getInt(
                context.getContentResolver(),
                Settings.System.SCREEN_OFF_TIMEOUT, 0);
    }

    private static String timeoutToString(int timeout) {
        String[] tags = new String[] {
                "second(s)",
                "minute(s)",
                "hour(s)"
            };

        // default to however many seconds we have
        String sTimeout = (timeout / 1000) + " " + tags[0];

        for(int i = 1; i < tags.length; i++) {
            int tmp = (timeout / 1000) / (60 * i);

            if(tmp < 60) {
                sTimeout = tmp + " " + tags[i];
            }
        }

        return sTimeout;
    }
}


