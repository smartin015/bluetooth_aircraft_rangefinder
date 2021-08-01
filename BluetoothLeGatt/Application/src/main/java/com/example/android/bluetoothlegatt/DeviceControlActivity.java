/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements OnInitListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private int MY_DATA_CHECK_CODE = 0;

    private TextView mConnectionState;
    private TextView mDistField;
    private TextView mTempField;
    private TextView mFluxField;
    private TextView mStatusField;
    private Button mTestButton;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private Menu mMenu;

    private boolean mSpeechActive;
    private TextToSpeech mTTS;

    // Keep track of prior sensor values
    public static final double DISTANCE_OFFSET = -1.77;
    public static final double MAX_REPORTED_DISTANCE = 30.0;
    public static final int DISTANCE_SENSITIVITY = 3;
    private int lastReportedDistance = 0;
    private double distance = 0;
    private double temp = 0;
    private int flux = 0;
    private String status = "";

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private Handler mSpeechHandler = new Handler();
    private Runnable mHandleSpeech = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "SpeechActive: " + mSpeechActive + ", distance: " + distance);
            int normDist = Math.max(0, (int)(distance));
            if (mSpeechActive && normDist < MAX_REPORTED_DISTANCE && Math.abs(normDist - lastReportedDistance) > DISTANCE_SENSITIVITY) {
                speak(String.format("%d", normDist));
                lastReportedDistance = normDist;
            }
            mSpeechHandler.postDelayed(this, 2000);
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                subscribeGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                distance = (intent.hasExtra(BluetoothLeService.SENSOR_DIST)) ? intent.getDoubleExtra(BluetoothLeService.SENSOR_DIST, 0) - DISTANCE_OFFSET: distance;
                temp = (intent.hasExtra(BluetoothLeService.SENSOR_TEMP)) ? intent.getDoubleExtra(BluetoothLeService.SENSOR_TEMP, 0) : temp;
                flux = (intent.hasExtra(BluetoothLeService.SENSOR_FLUX)) ? intent.getIntExtra(BluetoothLeService.SENSOR_FLUX, 0) : flux;
                status = (intent.hasExtra(BluetoothLeService.SENSOR_STATUS)) ? intent.getStringExtra(BluetoothLeService.SENSOR_STATUS) : status;
                displayData(String.format("%.2f ft", distance), String.format("%.2f C", temp), String.valueOf(flux), status);
            }
        }
    };

    private void subscribeCharacteristic(BluetoothGattCharacteristic characteristic) {
        final int charaProp = characteristic.getProperties();
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            mBluetoothLeService.readCharacteristic(characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            Log.d(TAG, "Setting up notify callback");
            mBluetoothLeService.setCharacteristicNotification(characteristic, true);
        }
    }

    private void clearUI() {
        mDistField.setText(R.string.no_data);
        mTempField.setText(R.string.no_data);
        mFluxField.setText(R.string.no_data);
        mStatusField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent checkTTSIntent = new Intent();
        checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE);
        mSpeechActive = false;

        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        if (mDeviceName == null) {
            mDeviceName = GattAttributes.LANDING_SENSOR_NAME;
        }
        if (mDeviceAddress == null) {
            mDeviceAddress = GattAttributes.LANDING_SENSOR_UUID;
        }

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = findViewById(R.id.connection_state);
        mDistField = findViewById(R.id.dist_value);
        mTempField = findViewById(R.id.temp_value);
        mFluxField = findViewById(R.id.flux_value);
        mStatusField = findViewById(R.id.status_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mTestButton = findViewById(R.id.test_height);
        mTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                distance = Math.random() * 1.5 * MAX_REPORTED_DISTANCE;
                Log.d(TAG, "Set distance to randomized " + distance);
                mDistField.setText(String.format("%.2f ft", distance));
            }
        });
    }

    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            mTTS.setLanguage(Locale.US);
        } else if (initStatus == TextToSpeech.ERROR) {
            Toast.makeText(this, "Sorry! Text To Speech failed...", Toast.LENGTH_LONG).show();
        }
    }

    private void speak(String speech) {
        mTTS.speak(speech, TextToSpeech.QUEUE_FLUSH, null);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                mTTS = new TextToSpeech(this, this);
            }
            else {
                Intent installTTSIntent = new Intent();
                installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installTTSIntent);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            mBluetoothLeService.initialize();
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        mSpeechHandler.post(mHandleSpeech);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        mSpeechHandler.removeCallbacks(mHandleSpeech);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        mTTS.stop();
        mTTS.shutdown();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        updateMenuState();
        return true;
    }

    private void updateMenuState() {
        mMenu.findItem(R.id.menu_connect).setVisible(!mConnected);
        mMenu.findItem(R.id.menu_disconnect).setVisible(mConnected);
        mMenu.findItem(R.id.menu_speech_enable).setVisible(!mSpeechActive);
        mMenu.findItem(R.id.menu_speech_disable).setVisible(mSpeechActive);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case R.id.menu_speech_disable:
                mSpeechActive = false;
                speak("spoken distance disabled");
                updateMenuState();
                return true;
            case R.id.menu_speech_enable:
                mSpeechActive = true;
                speak("ready");
                updateMenuState();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String dist, String temp, String flux, String status) {
        if (dist != null) {
            mDistField.setText(dist);
        }
        if (temp != null) {
            mTempField.setText(temp);
        }
        if (flux != null) {
            mFluxField.setText(flux);
        }
        if (status != null) {
            mStatusField.setText(status);
        }
    }

    // Subscribes to all listed GATT services and characteristics in GattAttributes.
    private void subscribeGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        for (BluetoothGattService gattService : gattServices) {
            if (GattAttributes.lookup(gattService.getUuid().toString()) == null) {
                Log.d(TAG, String.format("Skipping service %s", gattService.getUuid().toString()));
                continue;
            }
            Log.d(TAG, String.format("Service has %d characteristics", gattService.getCharacteristics().size()));
            for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                String uuid = gattCharacteristic.getUuid().toString();
                String name = GattAttributes.lookup(uuid);
                if (name == null) {
                    Log.d(TAG, String.format("Skipping characteristic %s", uuid));
                    continue;
                }
                Log.d(TAG, String.format("Found characteristic %s (%s)", uuid, name));
                subscribeCharacteristic(gattCharacteristic);
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
