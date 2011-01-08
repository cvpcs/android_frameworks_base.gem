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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
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

    private static final LinearLayout.LayoutParams BUTTON_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
                                        0,                                      // width = 0dip
                                        LinearLayout.LayoutParams.MATCH_PARENT, // height = match_parent
                                        1.0f                                    // weight = 1
                                        );


    private Context mContext;
    private LayoutInflater mInflater;
    private SettingsObserver mObserver = null;

    // button map [buttonid] => [buttonobj]
    private HashMap<String, PowerButton> mButtons = new HashMap<String, PowerButton>();

    public GalaxySWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // set up a broadcast receiver for our intents, based off of what our power buttons need
        IntentFilter filter = PowerButton.getAllBroadcastIntentFilters();
        // we add this so we can update views and such if the settings for our widget change
        filter.addAction(Settings.SETTINGS_CHANGED);
        context.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        // initial setup of our widget
        setupWidget();

        // set our visibility
        updateVisibility();
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

            if(pb != null && !mButtons.containsKey(button)) {
                Log.i(TAG, "Setting up button: " + button);
                // inflate our button, we don't add it to a parent and don't do any layout shit yet
                View buttonView = mInflater.inflate(R.layout.galaxy_s_widget_button, null, false);
                // add the button here
                addView(buttonView, BUTTON_LAYOUT_PARAMS);
                // set up our button around it
                pb.setupButton(buttonView);
                // store this for safe keeping
                mButtons.put(button, pb);
            }
        }
    }

    public void setupSettingsObserver(Handler handler) {
        if(mObserver == null) {
            mObserver = new SettingsObserver(handler);
            mObserver.observe();
        }
    }

    public void updateWidget() {
        for(PowerButton button : mButtons.values()) {
            button.update();
        }
    }

    public void updateVisibility() {
        // now check if we need to display the widget still
        boolean displayPowerWidget = Settings.System.getInt(mContext.getContentResolver(),
                   Settings.System.DISPLAY_GALAXY_S_WIDGET, 0) != 0;
        if(!displayPowerWidget) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
        }
    }

    // our own broadcast receiver :D
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // handle the intent through our power buttons
            PowerButton.handleOnReceive(context, intent);

            // update our widget
            updateWidget();
        }
    };

    // our own settings observer :D
    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            // watch for display widget
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.DISPLAY_GALAXY_S_WIDGET),
                            false, this);

            // watch for changes in buttons
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.GALAXY_S_WIDGET_BUTTONS),
                            false, this);

            // watch for changes in color
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.GALAXY_S_WIDGET_COLOR),
                            false, this);

            // watch for power-button specifc stuff
            for(Uri uri : PowerButton.getAllObservedUris()) {
                resolver.registerContentObserver(uri, false, this);
            }
        }

        @Override
        public void onChangeUri(Uri uri, boolean selfChange) {
            ContentResolver resolver = mContext.getContentResolver();
            Resources res = mContext.getResources();

            // first check if our widget buttons have changed
            if(uri.equals(Settings.System.getUriFor(Settings.System.GALAXY_S_WIDGET_BUTTONS))) {
                setupWidget();
            // now check if we change visibility
            } else if(uri.equals(Settings.System.getUriFor(Settings.System.DISPLAY_GALAXY_S_WIDGET))) {
                updateVisibility();
            }

            // do whatever the individual buttons must
            PowerButton.handleOnChangeUri(uri);

            // something happened so update the widget
            updateWidget();
        }
    }
}
