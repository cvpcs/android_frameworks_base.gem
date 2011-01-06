package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FlashlightButton extends PowerButton {
    private static final String TAG = "FlashlightButton";

    private static FlashlightButton OWN_BUTTON = null;

    private static boolean runTimer = false;

    private static final File SPOTLIGHT_FILE = new File("/sys/class/leds/spotlight/brightness");
    private static final File FLASHLIGHT_FILE = new File("/sys/class/leds/flashlight/brightness");
    private static File FLASHLIGHT = null;
    static {
        if (SPOTLIGHT_FILE.exists()) {
            FLASHLIGHT = SPOTLIGHT_FILE;
        } else if (FLASHLIGHT_FILE.exists()) {
            FLASHLIGHT = FLASHLIGHT_FILE;
        }
    }

    public FlashlightButton() { mType = PowerButton.BUTTON_FLASHLIGHT; }

    @Override
    public void updateState() {
        if(getFlashlightEnabled()) {
            mIcon = com.android.internal.R.drawable.stat_flashlight_on;
            mtState = STATE_ENABLED;
        } else {
            mIcon = com.android.internal.R.drawable.stat_flashlight_off;
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        if(FLASHLIGHT == null) {
            // flashlight doesn't exist, warn the user and return
            Toast msg = Toast.makeText(mView.getContext(),
                    "Flashlight is not supported on your device",
                    Toast.LENGTH_LONG);
            msg.setGravity(Gravity.CENTER, msg.getXOffset() / 2, msg.getYOffset() / 2);
            msg.show();
            return;
        }

        setFlashlightEnables(!getFlashlightEnabled());
    }

    public static FlashlightButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new FlashlightButton();
        return OWN_BUTTON;
    }

    private static boolean getFlashlightEnabled() {
        // catchall if flashlight isn't supported
        if(FLASHLIGHT == null) { return false; }

        int result = '0';

        try {
            FileReader fr = new FileReader(FLASHLIGHT);
            result = fr.read();
        } catch (Exception e) {
            Log.e(TAG, "getFlashlightEnabled failed", e);
        } finally {
            // if something goes wrong, try at least to close this
            try { fr.close(); } catch (Exception e) {
                Log.e(TAG, "setFlashlightEnabled failed", e);
            }
        }

        return (result != '0');
    }

    private static void setFlashlightEnabled(boolean enabled) {
        // catchall if flashlight isn't supported
        if(FLASHLIGHT == null) { return; }

        try {
            FileWriter fw = new FileWriter(FLASHLIGHT_FILE);
            int value = (on ? 1 : 0);
            fw.write(String.valueOf(value));
        } catch (Exception e) {
            Log.e(TAG, "setFlashlightEnabled failed", e);
        } finally {
            // if something goes wrong, try at least to close this
            try { fw.close(); } catch (Exception e) {
                Log.e(TAG, "setFlashlightEnabled failed", e);
            }
        }
    }
}
