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

package com.example.landingsensor;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity implements OnInitListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private int MY_DATA_CHECK_CODE = 0;

    private TextView mConnectionState;
    private TextView mDistField;
    private TextView mTempField;
    private TextView mFluxField;
    private TextView mTextStateField;
    private TextView mStatusField;
    private Button mTestButton;
    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private Menu mMenu;

    private boolean mSpeechActive;
    private TextToSpeech mTTS;

    private SharedPreferences mPreferences;

    // Keep track of prior sensor values
    public static final double DEFAULT_DISTANCE_OFFSET = -1.77;
    public static final double DEFAULT_MAX_REPORTED_DISTANCE = 30.0;
    public static final int DEFAULT_DISTANCE_SENSITIVITY = 2;
    public static final int DEFAULT_REPEAT_INTERVAL = 5;
    private Instant mLastSpokenDistance;
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

    private void handleSpeechHelper() {
        handleSpeechHelper(false);
    }

    private void handleSpeechHelper(boolean allow_repeat) {
        if (!mSpeechActive) {
            return;
        }
        // Preferences are live-reloaded
        final float DISTANCE_SENSITIVITY = Float.parseFloat(mPreferences.getString("edit_text_preference_distance_sensitivity", String.valueOf(DEFAULT_DISTANCE_SENSITIVITY)));
        final float MAX_REPORTED_DISTANCE = Float.parseFloat(mPreferences.getString("edit_text_preference_max_distance", String.valueOf(DEFAULT_MAX_REPORTED_DISTANCE)));
        int normDist = Math.max(0, (int)(distance));
        if (normDist >= MAX_REPORTED_DISTANCE) {
            return;
        }
        if (allow_repeat || Math.abs(normDist - lastReportedDistance) > DISTANCE_SENSITIVITY) {
            speak(String.format("%d", normDist));
            mLastSpokenDistance = (new Date()).toInstant();
            lastReportedDistance = normDist;
        }
    }

    private Handler mSpeechHandler = new Handler();
    private Runnable mHandleSpeech = new Runnable() {
        @Override
        public void run() {
            final boolean REPEAT_ENABLED = mPreferences.getBoolean("switch_preference_repeat_enabled", false);
            if (REPEAT_ENABLED) {
                final int REPEAT_INTERVAL = (int)(Float.parseFloat(mPreferences.getString("edit_text_repeat_interval", String.valueOf(DEFAULT_REPEAT_INTERVAL))));
                Instant now = (new Date()).toInstant();

                if (mLastSpokenDistance.plusSeconds(REPEAT_INTERVAL).compareTo(now) < 0) {
                    handleSpeechHelper(true);
                }
            } else {
                Log.d(TAG, "repeat_enabled=false, skipping report");
            }
            mSpeechHandler.postDelayed(this, 1000);
        }
    };

    private float getDistanceOffset() {
        return Float.parseFloat(mPreferences.getString("edit_text_preference_distance_offset", String.valueOf(DEFAULT_DISTANCE_OFFSET)));
    }

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
                distance = (intent.hasExtra(BluetoothLeService.SENSOR_DIST)) ? intent.getDoubleExtra(BluetoothLeService.SENSOR_DIST, 0) - getDistanceOffset() : distance;
                temp = (intent.hasExtra(BluetoothLeService.SENSOR_TEMP)) ? intent.getDoubleExtra(BluetoothLeService.SENSOR_TEMP, 0) : temp;
                flux = (intent.hasExtra(BluetoothLeService.SENSOR_FLUX)) ? intent.getIntExtra(BluetoothLeService.SENSOR_FLUX, 0) : flux;
                status = (intent.hasExtra(BluetoothLeService.SENSOR_STATUS)) ? intent.getStringExtra(BluetoothLeService.SENSOR_STATUS) : status;
                displayData(
                        String.format("%.2f ft", distance),
                        String.format("%.2f C", temp),
                        String.valueOf(flux),
                        status);
                handleSpeechHelper();
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
        mTextStateField.setText(R.string.n_a);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        getSupportActionBar().show();

        Intent checkTTSIntent = new Intent();
        checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE);
        mLastSpokenDistance = (new Date()).toInstant();

        mSpeechActive = mPreferences.getBoolean("switch_preference_start_with_voice_enabled", false);

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
        mConnectionState = findViewById(R.id.state_value);
        mDistField = findViewById(R.id.dist_value);
        mTempField = findViewById(R.id.temp_value);
        mFluxField = findViewById(R.id.flux_value);
        mTextStateField = findViewById(R.id.text_state_value);
        mStatusField = findViewById(R.id.status_value);
        setUISpeechActive();


        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        mTestButton = findViewById(R.id.test_height_inc);
        mTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                distance += 1.0;
                Log.d(TAG, "Inc distance: " + distance);
                mDistField.setText(String.format("%.2f ft", distance));
                handleSpeechHelper();
            }
        });

        mTestButton = findViewById(R.id.test_height_dec);
        mTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                distance = Math.max(0, distance - 1.0);
                Log.d(TAG, "Dec distance: " + distance);
                mDistField.setText(String.format("%.2f ft", distance));
                handleSpeechHelper();
            }
        });

        Log.d(TAG, "Distance offset: " + getDistanceOffset());
    }

    public void onInit(int initStatus) {
        if (initStatus == TextToSpeech.SUCCESS) {
            mTTS.setLanguage(Locale.US);
            final float SPEECH_RATE = Float.parseFloat(mPreferences.getString("edit_text_preference_speech_rate", "1.0"));
            Log.d(TAG, "Speech rate: " + SPEECH_RATE);
            mTTS.setSpeechRate(SPEECH_RATE);
        } else if (initStatus == TextToSpeech.ERROR) {
            Toast.makeText(this, "Sorry! Text To Speech failed...", Toast.LENGTH_LONG).show();
        }
    }

    private void speak(String speech) {
        mTTS.speak(speech, TextToSpeech.QUEUE_ADD, null);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
                setUISpeechActive();
                speak("audible height off");
                updateMenuState();
                return true;
            case R.id.menu_speech_enable:
                mSpeechActive = true;
                setUISpeechActive();
                speak("audible height on");
                updateMenuState();
                return true;
            case R.id.menu_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
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

    private void setUISpeechActive() {
        mTextStateField.setText((mSpeechActive) ? "On": "Off");
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
