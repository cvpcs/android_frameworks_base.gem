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
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import com.android.internal.R;

import java.util.ArrayList;
import java.util.HashMap;

public class GalaxySWidget extends LinearLayout {
    private static final String TAG = "GalaxySWidget";

    public static final String BUTTON_DELIMITER = "|";

    private static final String BUTTONS_DEFAULT = PowerButton.BUTTON_WIFI
                             + BUTTON_DELIMITER + PowerButton.BUTTON_BLUETOOTH
                             + BUTTON_DELIMITER + PowerButton.BUTTON_GPS
                             + BUTTON_DELIMITER + PowerButton.BUTTON_SOUND;

    private Context mContext;
    private LayoutInflater mInflater;
    private HashMap<String, PowerButton> mButtons;

    public GalaxySWidgetButton(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public onFinishInflate() {
        super.onFinishInflate();

        // initial setup of our widget
        setupWidget();
    }

    public void setupWidget() {
        Log.i(TAG, "Clearing any old widget stuffs");
        // remove all views from the layout
        removeAllViews();

        // clear the button instances
        for(PowerButton button : mButtons.values()) {
            button.setupButton(null);
        }

        // clear our array of buttons
        mButtons.clear();

        Log.i(TAG, "Setting up widget");

        String buttons = Settings.System.getString(mContext.getContentResolver(), Settings.System.GALAXY_S_WIDGET_BUTTONS);
        if(buttons == null) {
            Log.i(TAG, "Default buttons being loaded");
            buttons = BUTTONS_DEFAULT;
        }
        Log.i(TAG, "Button list: " + buttons);

        mButtons = new HashMap<String, PowerButton>();
        for(String button : buttons.split("\\|")) {
            PowerButton pb = PowerButton.getButtonInstance(button);

            if(pb != null && !list.contains(button)) {
                Log.i(TAG, "Setting up button: " + button);
                View buttonView = mInflater.inflate(R.layout.galaxy_s_widget_button, this);
                // add our view to the layout
                addView(buttonView);
                // set up our button around it
                pb.setupButton(buttonView);
                // store this for safe keeping
                mButtons.put(button, pb);
            }
        }
    }

    public void updateWidget() {
        for(PowerButton button : mButtons.values()) {
            button.updateState();
            button.updateView();
        }
    }
}
