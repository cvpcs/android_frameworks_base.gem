package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.view.KeyEvent;

public class MediaPreviousButton extends MediaKeyEventButton {
    private static MediaPreviousButton OWN_BUTTON = null;

    public MediaPreviousButton() { mType = BUTTON_MEDIA_PREVIOUS; }

    @Override
    public void updateState() {
        mIcon = com.android.internal.R.drawable.stat_media_previous;
        if(getAudioManager(mView.getContext()).isMusicActive()) {
            mState = STATE_ENABLED;
        } else {
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    public static MediaPreviousButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new MediaPreviousButton();
        return OWN_BUTTON;
    }
}
