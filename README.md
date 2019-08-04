# bluetooth_aircraft_rangefinder

This is a rangefinder in two parts:

1. ble_rangefinder: An ESP32-based BLE server that reads a distance sensor via UART
1. BluetoothLEGatt: An android app that pairs with the device, reads its data, and reports it via screen and periodic voice command

## Setup

See https://github.com/espressif/arduino-esp32 for instructions on setting up Arduino for ESP32, also the
header comments of `ble_rangefinder.ino` for information on which libraries need to be installed.

Install Android Studio (https://developer.android.com/studio) and refer to https://developer.android.com/studio/run to build/run BluetoothLEGatt from source; otherwise
install the prebuilt `.apk` file in this directory by downloading it to your phone and opening it.

## Details

See https://www.oreilly.com/library/view/getting-started-with/9781491900550/ch04.html for documentation on Bluetooth LE concepts like
services, characteristics, and descriptors.

In order to receive messages, the android app first turns on its own notifications when there's BLE data for a particular characteristic, then it
sends a message to the device (via its CCCD, or Client Configuration Characteristic Descriptor) asking it to send periodic notifications.

Most of the business logic in the app lives in `DeviceControlActivity.java`
