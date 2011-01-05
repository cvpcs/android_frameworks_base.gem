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

import java.util.HashMap;
import java.util.Map;

public abstract class PowerButton {
    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TURNING_ON = 3;
    public static final int STATE_TURNING_OFF = 4;
    public static final int STATE_INTERMEDIATE = 5;
    public static final int STATE_UNKNOWN = 6;

    public static final String BUTTON_WIFI = "wifi";
    public static final String BUTTON_GPS = "gps";
    public static final String BUTTON_BLUETOOTH = "bluetooth";
    public static final String BUTTON_BRIGHTNESS = "brightness";
    public static final String BUTTON_SOUND = "sound";
    public static final String BUTTON_SYNC = "sync";
    public static final String BUTTON_WIFIAP = "wifiap";
    public static final String BUTTON_SCREENTIMEOUT = "screentimeout";
    public static final String BUTTON_MOBILEDATA = "mobiledata";
    public static final String BUTTON_LOCKSCREEN = "lockscreen";
    public static final String BUTTON_NETWORKMODE = "networkmode";
    public static final String BUTTON_AUTOROTATE = "autorotate";
    public static final String BUTTON_AIRPLANE = "airplane";
    public static final String BUTTON_UNKNOWN = "unknown";

    private static final Mode MASK_MODE = Mode.SCREEN;
    private static final HashMap<String, PowerButton> BUTTONS = new HashMap<String, PowerButton>();
    static {
        BUTTONS.put(BUTTON_WIFI, WifiButton.getInstance());
        BUTTONS.put(BUTTON_GPS, GPSButton.getInstance());
        BUTTONS.put(BUTTON_BLUETOOTH, BluetoothButton.getInstance());
        BUTTONS.put(BUTTON_BRIGHTNESS, BrightnessButton.getInstance());
        BUTTONS.put(BUTTON_SOUND, SoundButton.getInstance());
        BUTTONS.put(BUTTON_SYNC, SyncButton.getInstance());
        BUTTONS.put(BUTTON_WIFIAP, WifiApButton.getInstance());
        BUTTONS.put(BUTTON_SCREENTIMEOUT, ScreenTimeoutButton.getInstance());
        BUTTONS.put(BUTTON_MOBILEDATA, MobileDataButton.getInstance());
        BUTTONS.put(BUTTON_LOCKSCREEN, LockScreenButton.getInstance());
        BUTTONS.put(BUTTON_NETWORKMODE, NetworkModeButton.getInstance());
        BUTTONS.put(BUTTON_AUTOROTATE, AutoRotateButton.getInstance());
        BUTTONS.put(BUTTON_AIRPLANE, AirplaneButton.getInstance());
    }

    protected int mIcon;
    protected int mState;
    protected View mView;
    protected String mType = BUTTON_UNKNOWN;

    public abstract void updateState();
    protected abstract void toggleState();

    public static PowerButton getButtonInstance(String button) {
        if(BUTTONS.containsKey(button)) {
            return BUTTONS.get(button);
        } else {
            return null;
        }
    }

    public void setupButton(View view) {
        mView = view;
        if(mView != null) {
            mView.setTag(mType);
            mView.setOnClickListener(mClickListener);
        }
    }

    public void updateView() {
        if(mView != null) {
            Context context = mView.getContext();
            Resources res = context.getResources();
            int buttonLayer = R.id.galaxy_s_widget_button;
			int buttonIcon = R.id.galaxy_s_widget_button_image;
			int buttonState = R.id.galaxy_s_widget_button_indic;

			updateImageView(buttonIcon, mIcon);

			int sColorMaskBase = Settings.System.getInt(context.getContentResolver(),
				Settings.System.GALAXY_S_WIDGET_COLOR, 0xFF00EFFF);
			int sColorMaskOn    = (sColorMaskBase & 0x00FFFFFF) | 0xA0000000;
			int sColorMaskOff   = (sColorMaskBase & 0x00FFFFFF) | 0x33000000;
			int sColorMaskInter = (sColorMaskBase & 0x00FFFFFF) | 0x60000000;

			/* Button State */
			switch(mState) {
			case STATE_ENABLED:
				updateImageView(buttonState,
					 res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskOn, MASK_MODE));
				break;
			case STATE_DISABLED:
				updateImageView(buttonState,
					 res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskOff, MASK_MODE));
				break;
			default:
				updateImageView(buttonState,
					 res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskInter, MASK_MODE));
				break;
			}
        }
    }

    private void updateImageView(int id, int resId) {
        ImageView imageIcon = (ImageView)mView.findViewById(id);
        imageIcon.setImageResource(resId);
    }

    private void updateImageView(int id, Drawable resDraw) {
        ImageView imageIcon = (ImageView)mView.findViewById(id);
        imageIcon.setImageResource(R.drawable.stat_bgon_custom);
        imageIcon.setImageDrawable(resDraw);
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            String type = (String)v.getTag();

            for(Map.Entry<String, PowerButton> entry : BUTTONS.entrySet()) {
                if(entry.getKey().equals(type)) {
                    entry.getValue().toggleState();
                }
            }
        }
    };
}
