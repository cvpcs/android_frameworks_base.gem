package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.view.KeyEvent;

public class MediaNextButton extends MediaKeyEventButton {
    private static MediaNextButton OWN_BUTTON = null;

    public MediaNextButton() { mType = BUTTON_MEDIA_NEXT; }

    @Override
    public void updateState() {
        mIcon = com.android.internal.R.drawable.stat_media_next;
        if(getAudioManager(mView.getContext()).isMusicActive()) {
            mState = STATE_ENABLED;
        } else {
            mState = STATE_DISABLED;
        }
    }

    @Override
    protected void toggleState() {
        sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public static MediaNextButton getInstance() {
        if (OWN_BUTTON==null) OWN_BUTTON = new MediaNextButton();
        return OWN_BUTTON;
    }
}
