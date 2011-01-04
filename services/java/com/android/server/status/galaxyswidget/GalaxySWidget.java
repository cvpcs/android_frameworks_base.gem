/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.status.galaxyswidget;

import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;

import com.android.internal.R;

import java.util.ArrayList;

public class GalaxySWidget extends LinearLayout {
    private static final String TAG = "GalaxySWidget";

    public static final String BUTTON_DELIMITER = "|";

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

    private static final ArrayList<String> BUTTONS = new ArrayList<String>();
    static {
        BUTTONS.add(BUTTON_WIFI);
        BUTTONS.add(BUTTON_GPS);
        BUTTONS.add(BUTTON_BLUETOOTH);
        BUTTONS.add(BUTTON_BRIGHTNESS);
        BUTTONS.add(BUTTON_SOUND);
        BUTTONS.add(BUTTON_SYNC);
        BUTTONS.add(BUTTON_WIFIAP);
        BUTTONS.add(BUTTON_SCREENTIMEOUT);
        BUTTONS.add(BUTTON_MOBILEDATA);
        BUTTONS.add(BUTTON_LOCKSCREEN);
        BUTTONS.add(BUTTON_NETWORKMODE);
        BUTTONS.add(BUTTON_AUTOROTATE);
        BUTTONS.add(BUTTON_AIRPLANE);
    }

    private static final String BUTTONS_DEFAULT = BUTTON_WIFI
                             + BUTTON_DELIMITER + BUTTON_BLUETOOTH
                             + BUTTON_DELIMITER + BUTTON_GPS
                             + BUTTON_DELIMITER + BUTTON_SOUND;

    private Context mContext;

    public GalaxySWidgetButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
    }

    @Override
    public onFinishInflate() {
        super.onFinishInflate();

        // initial setup of our widget
        setupWidget();
    }

    public setupWidget() {
        Log.i(TAG, "Setting up widget");

        String buttons = Settings.System.getString(mContext.getContentResolver(), Settings.System.GALAXY_S_WIDGET_BUTTONS);
        if(buttons == null) {
            Log.i(TAG, "Default buttons being loaded");
            buttons = BUTTONS_DEFAULT;
        }
        Log.i(TAG, "Button list: " + buttons);

        ArrayList<String> list = new ArrayList<String>();
        for(String button : buttons.split("\\|")) {
            if(BUTTONS.contains(button) && !list.contains(button)) {
                list.add(button);
            }
        }
    }

    public clearWidget() {
    }
}
