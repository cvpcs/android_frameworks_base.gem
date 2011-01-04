package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.provider.Settings;
import android.view.View;

import com.android.server.status.ExpandedView;

public abstract class PowerButton {
    private Mode mMaskMode = Mode.SCREEN;
    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TURNING_ON = 3;
    public static final int STATE_TURNING_OFF = 4;
    public static final int STATE_INTERMEDIATE = 5;
    public static final int STATE_UNKNOWN = 6;

    public int currentIcon;
    public int currentState;
    public View currentView;

    abstract void initButton(int position);
    abstract public void toggleState(Context context);
    public abstract void updateState(Context context);

    public void setupButton(View view) {
        currentView = view;
    }

    public void updateView(Context context) {
        Resources res = context.getResources();
        int buttonLayer = R.id.galaxy_s_widget_button;
        int buttonIcon = R.id.galaxy_s_widget_button_image;
        int buttonState = R.id.galaxy_s_widget_button_indic;

        updateImageView(buttonIcon, currentIcon);

        int sColorMaskBase = Settings.System.getInt(context.getContentResolver(),
                Settings.System.GALAXY_S_WIDGET_COLOR, 0xFF00EFFF);
        int sColorMaskOn    = (sColorMaskBase & 0x00FFFFFF) | 0xA0000000;
        int sColorMaskOff   = (sColorMaskBase & 0x00FFFFFF) | 0x33000000;
        int sColorMaskInter = (sColorMaskBase & 0x00FFFFFF) | 0x60000000;

        /* Button State */
        switch(currentState) {
            case STATE_ENABLED:
                updateImageView(buttonState,
                         res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskOn, mMaskMode));
                break;
            case STATE_DISABLED:
                updateImageView(buttonState,
                         res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskOff, mMaskMode));
                break;
            default:
                updateImageView(buttonState,
                         res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskInter, mMaskMode));
                break;
        }
    }

    private void updateImageView(int id, int resId) {
        ImageView imageIcon = (ImageView)currentView.findViewById(id);
        imageIcon.setImageResource(resId);
    }

    private void updateImageView(int id, Drawable resDraw) {
        ImageView imageIcon = (ImageView)currentView.findViewById(id);
        imageIcon.setImageResource(R.drawable.stat_bgon_custom);
        imageIcon.setImageDrawable(resDraw);
    }
}
