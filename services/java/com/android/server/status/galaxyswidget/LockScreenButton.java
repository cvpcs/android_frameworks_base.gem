package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.widget.Toast;

public class LockScreenButton extends PowerButton {

    private static Boolean LOCK_SCREEN_STATE = null;
    private static LockScreenButton OWN_BUTTON = null;

    private KeyguardLock mLock = null;

    public LockScreenButton() { mType = PowerButton.BUTTON_LOCKSCREEN; }

    @Override
    public void updateState() {
        getState(mView.getContext());
        if (LOCK_SCREEN_STATE == null) {
            mIcon = R.drawable.stat_lock_screen_off;
            mState = PowerButton.STATE_INTERMEDIATE;
        } else if (LOCK_SCREEN_STATE) {
            mIcon = R.drawable.stat_lock_screen_on;
            mState = PowerButton.STATE_ENABLED;
        } else {
            mIcon = R.drawable.stat_lock_screen_off;
            mState = PowerButton.STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        getState();
        if(LOCK_SCREEN_STATE == null) {
            Toast msg = Toast.makeText(context, "Not yet initialized", Toast.LENGTH_LONG);
            msg.setGravity(Gravity.CENTER, msg.getXOffset() / 2, msg.getYOffset() / 2);
            msg.show();
        } else {
            getLock(context);
            if (mLock != null) {
                if (LOCK_SCREEN_STATE) {
                  mLock.disableKeyguard();
                  LOCK_SCREEN_STATE = false;
                } else {
                  mLock.reenableKeyguard();
                  LOCK_SCREEN_STATE = true;
                }
            }
        }
    }

    public static LockScreenButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new LockScreenButton();
        return OWN_BUTTON;
    }

    private KeyguardLock getLock(Context context) {
        if (mLock == null) {
            KeyguardManager keyguardManager = (KeyguardManager)context.
                    getSystemService(Activity.KEYGUARD_SERVICE);
            mLock = keyguardManager.newKeyguardLock(Context.KEYGUARD_SERVICE);
        }
        return mLock;
    }

    private static boolean getState() {
        if (LOCK_SCREEN_STATE == null) {
            LOCK_SCREEN_STATE = true;
        }
        return LOCK_SCREEN_STATE;
    }
}

