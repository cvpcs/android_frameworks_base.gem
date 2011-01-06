package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.provider.Settings;

public class SoundButton extends PowerButton {

    private static SoundButton OWN_BUTTON = null;

    public static final int RINGER_MODE_UNKNOWN = 0;
    public static final int RINGER_MODE_SILENT = 1;
    public static final int RINGER_MODE_VIBRATE_ONLY = 2;
    public static final int RINGER_MODE_SOUND_ONLY = 3;
    public static final int RINGER_MODE_SOUND_AND_VIBRATE = 4;

    public SoundButton() { mType = PowerButton.BUTTON_SOUND; }

    @Override
    public void updateState() {
        switch (getSoundState(mView.getContext())) {
        case RINGER_MODE_SOUND_AND_VIBRATE:
                mIcon = R.drawable.stat_ring_on;
                mState = PowerButton.STATE_ENABLED;
            break;
        case RINGER_MODE_SOUND_ONLY:
                mIcon = R.drawable.stat_ring_on;
                mState = PowerButton.STATE_INTERMEDIATE;
            break;
        case RINGER_MODE_VIBRATE_ONLY:
                mIcon = R.drawable.stat_vibrate_off;
                mState = PowerButton.STATE_DISABLED;
            break;
        case RINGER_MODE_SILENT:
                mIcon = R.drawable.stat_silent;
                mState = PowerButton.STATE_DISABLED;
            break;

        }
    }

    @Override
    protected void toggleState() {
        Context context = mView.getContext();
        int currentState = getSoundState(context);
        AudioManager mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        switch (currentState) {
        case RINGER_MODE_SOUND_AND_VIBRATE: // go back to silent, no vibrate
            Settings.System.putInt(context.getContentResolver(),Settings.System.VIBRATE_IN_SILENT,0);
            mAudioManager.
                setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_OFF);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            break;
        case RINGER_MODE_SOUND_ONLY: // go to sound and vibrate
            Settings.System.putInt(context.getContentResolver(),Settings.System.VIBRATE_IN_SILENT,1);
            mAudioManager.
                setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_ON);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            break;
        case RINGER_MODE_VIBRATE_ONLY: // go to sound
            Settings.System.putInt(context.getContentResolver(),Settings.System.VIBRATE_IN_SILENT,1);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_ONLY_SILENT);
            break;
        case RINGER_MODE_SILENT: // go to vibrate
            Settings.System.putInt(context.getContentResolver(),Settings.System.VIBRATE_IN_SILENT,1);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_ONLY_SILENT);
            break;
        default: // default going to sound
            Settings.System.putInt(context.getContentResolver(),Settings.System.VIBRATE_IN_SILENT,1);
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            mAudioManager.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,AudioManager.VIBRATE_SETTING_ONLY_SILENT);
            break;
        }
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        // note, we don't actually have an "onReceive", so the caught intent will be ignored, but we want
        // to catch it anyway so the ringer status is updated if changed externally :D
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        return filter;
    }

    public static SoundButton getInstance() {
        if (OWN_BUTTON == null) OWN_BUTTON = new SoundButton();
        return OWN_BUTTON;
    }

    private static int getSoundState(Context context) {
        AudioManager mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        int ringMode = mAudioManager.getRingerMode();
        int vibrateMode = mAudioManager.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);

        if (ringMode == AudioManager.RINGER_MODE_NORMAL && vibrateMode == AudioManager.VIBRATE_SETTING_ON) {
            return RINGER_MODE_SOUND_AND_VIBRATE;
        } else if (ringMode == AudioManager.RINGER_MODE_NORMAL) {
            return RINGER_MODE_SOUND_ONLY;
        } else if (ringMode == AudioManager.RINGER_MODE_VIBRATE) {
            return RINGER_MODE_VIBRATE_ONLY;
        } else if (ringMode == AudioManager.RINGER_MODE_SILENT) {
            return RINGER_MODE_SILENT;
        } else {
            return RINGER_MODE_UNKNOWN;
        }
    }
}
