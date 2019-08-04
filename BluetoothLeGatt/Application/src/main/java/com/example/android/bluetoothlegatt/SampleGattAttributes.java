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

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    static final String LANDING_SENSOR_UUID = "24:0A:C4:9C:B7:FE";
    static final String LANDING_SENSOR_NAME = "Landing Sensor";
    private static HashMap<String, String> attributes = new HashMap();
    public static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static final String SENSOR_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String SENSOR_DIST_CHARACTERISTIC = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String SENSOR_TEMP_CHARACTERISTIC = "6e400005-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String SENSOR_FLUX_CHARACTERISTIC = "6e400004-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String SENSOR_STATUS_CHARACTERISTIC = "6e400006-b5a3-f393-e0a9-e50e24dcca9e";

    static {
        attributes.put(SENSOR_SERVICE, "Landing Sensor");
        attributes.put(SENSOR_DIST_CHARACTERISTIC, "Distance");
        attributes.put(SENSOR_TEMP_CHARACTERISTIC, "Temperature (C)");
        attributes.put(SENSOR_FLUX_CHARACTERISTIC, "Flux (strength)");
        attributes.put(SENSOR_STATUS_CHARACTERISTIC, "TFMPlus Status");
    }

    public static String lookup(String uuid) {
        return attributes.get(uuid);
    }
}
