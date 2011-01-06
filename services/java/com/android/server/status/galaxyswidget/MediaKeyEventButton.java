package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

public abstract class MediaKeyEventButton extends PowerButton {
    private static AudioManager AUDIO_MANAGER = null;

    @Override
    public void setupButton(View view) {
        super.setupButton(view);

        // we register our a global playback listener so that our buttons will update when something happens
        if(mView == null) {
            // view == null means clear config, so unregister our listener
            MediaPlayer.unregisterGlobalOnPlaybackStateChangedListener(mOnPlaybackStateChangedListener);
        } else {
            // view isn't null so setting up, register our listener
            MediaPlayer.registerGlobalOnPlaybackStateChangedListener(mOnPlaybackStateChangedListener);
        }
    }
    protected void sendMediaKeyEvent(int code) {
        Context context = mView.getContext();
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        context.sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        context.sendOrderedBroadcast(upIntent, null);
    }

    protected static AudioManager getAudioManager(Context context) {
        if(AUDIO_MANAGER == null) {
            AUDIO_MANAGER = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }

        return AUDIO_MANAGER;
    }

    private MediaPlayer.OnPlaybackStateChangedListener mOnPlaybackStateChangedListener = new MediaPlayer.OnPlaybackStateChangedListener() {
            public void onPlaybackStateChanged(MediaPlayer mp) {
                updateState();
            }
        };
}
