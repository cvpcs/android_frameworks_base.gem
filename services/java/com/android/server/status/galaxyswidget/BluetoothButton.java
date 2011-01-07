package com.android.server.status.galaxyswidget;

import com.android.internal.R;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;

public class BluetoothButton extends PowerButton {

    private static final StateTracker sBluetoothState = new BluetoothStateTracker();

    private static BluetoothButton OWN_BUTTON = null;

    private static final class BluetoothStateTracker extends StateTracker {

        @Override
        public int getActualState(Context context) {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBluetoothAdapter == null) {
                return STATE_UNKNOWN; // On emulator?
            }
            return bluetoothStateToFiveState(mBluetoothAdapter
                    .getState());
        }

        @Override
        protected void requestStateChange(Context context,
                final boolean desiredState) {
            // Actually request the Bluetooth change and persistent
            // settings write off the UI thread, as it can take a
            // user-noticeable amount of time, especially if there's
            // disk contention.
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... args) {
                    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if(mBluetoothAdapter.isEnabled()) {
                        mBluetoothAdapter.disable();
                    } else {
                        mBluetoothAdapter.enable();
                    }
                    return null;
                }
            }.execute();
        }

        @Override
        public void onActualStateChange(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent
                    .getAction())) {
                return;
            }
            int bluetoothState = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE, -1);
            setCurrentState(context, bluetoothStateToFiveState(bluetoothState));
        }

        /**
         * Converts BluetoothAdapter's state values into our
         * Wifi/Bluetooth-common state values.
         */
        private static int bluetoothStateToFiveState(int bluetoothState) {
            switch (bluetoothState) {
            case BluetoothAdapter.STATE_OFF:
                return STATE_DISABLED;
            case BluetoothAdapter.STATE_ON:
                return STATE_ENABLED;
            case BluetoothAdapter.STATE_TURNING_ON:
                return STATE_TURNING_ON;
            case BluetoothAdapter.STATE_TURNING_OFF:
                return STATE_TURNING_OFF;
            default:
                return STATE_UNKNOWN;
            }
        }
    }

    public BluetoothButton() { mType = BUTTON_BLUETOOTH; }

    @Override
    public void updateState() {
        mState = sBluetoothState.getTriState(mView.getContext());
        switch (mState) {
        case STATE_DISABLED:
            mIcon = R.drawable.stat_bluetooth_off;
            break;
        case STATE_ENABLED:
            mIcon = R.drawable.stat_bluetooth_on;
            break;
        case STATE_INTERMEDIATE:
            // In the transitional state, the bottom green bar
            // shows the tri-state (on, off, transitioning), but
            // the top dark-gray-or-bright-white logo shows the
            // user's intent. This is much easier to see in
            // sunlight.
            if (sBluetoothState.isTurningOn()) {
                mIcon = R.drawable.stat_bluetooth_on;
            } else {
                mIcon = R.drawable.stat_bluetooth_off;
            }
            break;
        }
    }

    @Override
    public void toggleState() {
        sBluetoothState.toggleState(mView.getContext());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        sBluetoothState.onActualStateChange(context, intent);
    }

    @Override
    protected IntentFilter getBroadcastIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return filter;
    }

    public static BluetoothButton getInstance() {
        if (OWN_BUTTON == null) OWN_BUTTON = new BluetoothButton();
        return OWN_BUTTON;
    }
}