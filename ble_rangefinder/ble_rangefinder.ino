/* Lolin D32 Bluetooth LE Rangefinder
 *  Publishes the sensor data from a TFMini Plus IR distance sensor.
 *  See https://cdn.sparkfun.com/assets/1/4/2/1/9/TFmini_Plus_A02_Product_Manual_EN.pdf
 *  for the product manual.
 *  
 *  It uses a Lolin D32 board which runs an ESP32-WROOM-32 packaged wifi+BLE microcontroller.
 *  See https://wiki.wemos.cc/products:d32:d32 for controller details.
 *  
 *  This was built using the following libraries:
 *  - TFMPlus 1.3.4
 *  - ESP32 BLE Arduino 1.0.1
 *  
 *  The TFMPlus has four wires. Wiring is as follows:
 *  - RED   (TFMPlus VCC) -- USB (Lolin D32)
 *  - BLACK (TFMPlus GND) -- GND (Lolin D32)
 *  - WHITE (TFMPlus RXD) <- 15  (Lolin D32 TXD)
 *  - GREEN (TFMPlus TXD) -> 4   (Lolin D32 RXD)
 */

#include <TFMPlus.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

TFMPlus tfmP;
#define D32_RXD 4
#define D32_TXD 15
#define SAMPLE_RATE_HZ 10
#define TEMP_FLUX_REPORT_PD 20
uint16_t tfDist;       // Distance measurement in centimeters (default)
uint16_t tfFlux;       // Luminous flux or intensity of return signal
uint16_t tfTemp;       // Temperature in degrees Centigrade (coded)
uint16_t loopCount;    // Loop counter (1-20)
uint64_t lastSample = 0;

BLEServer *pServer = NULL;
BLE2902 * pDistCCCD;
BLECharacteristic * pDistCharacteristic;
BLECharacteristic * pFluxCharacteristic;
BLECharacteristic * pTempCharacteristic;
BLECharacteristic * pStatusCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_DIST "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_FLUX "6E400004-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TEMP "6E400005-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_STATUS "6E400006-B5A3-F393-E0A9-E50E24DCCA9E"

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
    };
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
    }
};

void printTFMPFirmwareVersion() {
  for( uint8_t fvi = 1; fvi < 4; ++fvi)
  {
    if( tfmP.sendCommand(OBTAIN_FIRMWARE_VERSION, 0)) {
      Serial.print("Firmware version: ");
      Serial.print(tfmP.version[0]);
      Serial.print(".");
      Serial.print(tfmP.version[1]);
      Serial.print(".");
      Serial.print(tfmP.version[2]);
      Serial.println(".");
      return;
    } else {
      Serial.println("Get firmware version failed.");
      tfmP.printStatus(false);
    }
    delay(100);  // Wait to try again
  }
}

void setupBLE() {
  BLEDevice::init("Landing Sensor");

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Same CCCD is shared across all characteristics, so 
  // notification enable/disable is also shared.
  pDistCCCD = new BLE2902();
  pDistCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_DIST, BLECharacteristic::PROPERTY_NOTIFY);
  pDistCharacteristic->addDescriptor(pDistCCCD);

  pFluxCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_FLUX, BLECharacteristic::PROPERTY_NOTIFY);
  pFluxCharacteristic->addDescriptor(pDistCCCD);
                    
  pTempCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_TEMP, BLECharacteristic::PROPERTY_NOTIFY);
  pTempCharacteristic->addDescriptor(pDistCCCD);

  pStatusCharacteristic = pService->createCharacteristic(CHARACTERISTIC_UUID_STATUS, BLECharacteristic::PROPERTY_NOTIFY);
  pStatusCharacteristic->addDescriptor(pDistCCCD);
  pService->start();
}

void setupTFMP() {
  Serial1.begin(115200, SERIAL_8N1, D32_RXD, D32_TXD); //Baud rate, parity mode, RX, TX
  delay(20); // Give port time to initalize
  tfmP.begin( &Serial1);
}

void setup() {
  loopCount = 0;
  tfDist = 0;
  tfFlux = 0;
  tfTemp = 0;
  
  Serial.begin(115200);
  
  Serial.println("Setting up BLE");
  setupBLE();
  
  Serial.println("Setting up TFMPlus");
  setupTFMP();
  printTFMPFirmwareVersion();

  pServer->getAdvertising()->start();
  Serial.println("Waiting for client...");
}

void onDisconnect() {
  delay(500); // give the bluetooth stack the chance to get things ready
  pServer->startAdvertising();
  oldDeviceConnected = deviceConnected;
}

void onConnect() {
  oldDeviceConnected = deviceConnected;
}

void loopTFMP() {
  if (!tfmP.getData(tfDist, tfFlux, tfTemp)) {
    return;
  }

  Serial.print(".");
  pDistCharacteristic->setValue(tfDist);
  pDistCharacteristic->notify();
  
  if(loopCount >= TEMP_FLUX_REPORT_PD) {
    String tfStatus = "UNKNOWN";
    switch (tfmP.status) {
      case TFMP_READY:
        tfStatus = "Ready";
        break;
      case TFMP_SERIAL:
        tfStatus = "Serial connection error";
        break;
      case TFMP_HEADER:
        tfStatus = "Header parser error";
        break;
      case TFMP_CHECKSUM:
        tfStatus = "Checksum error";
        break;
      case TFMP_TIMEOUT:
        tfStatus = "Timeout";
        break;
      case TFMP_PASS:
        tfStatus = "Pass";
        break;
      case TFMP_FAIL:
        tfStatus = "Fail";
        break;
      default:
        break;
    }
    pStatusCharacteristic->setValue(tfStatus.c_str());
    pStatusCharacteristic->notify();

    // Display signal strength in arbitrary units.
    pFluxCharacteristic->setValue(tfFlux);
    pFluxCharacteristic->notify();

    // Decode temperature data and display as 10ths of degrees Centigrade.
    uint16_t tfTempDeciC = ((float(tfTemp) / 8) - 256)*10;
    pTempCharacteristic->setValue(tfTempDeciC);
    pTempCharacteristic->notify();

    char buf[128];
    sprintf(buf, "%d cm\t%d dC\t%d Flx", tfDist, tfTempDeciC, tfFlux);
    Serial.println(buf);
    loopCount = 0;
  }
  loopCount++;
}

int sampleRate = -1;
void loop() {
  if (pDistCCCD->getNotifications() && sampleRate != SAMPLE_RATE_HZ) {
    if(!tfmP.sendCommand(SET_FRAME_RATE, SAMPLE_RATE_HZ)) {
      Serial.println("Set sample rate failed:");
      tfmP.printStatus(false);
    } else {
      Serial.println("TFMPlus Sample rate set");
      sampleRate = SAMPLE_RATE_HZ;
    }
    lastSample = millis();
  }

  if (!pDistCCCD->getNotifications() && sampleRate > 0) {
    // Keep frame rate off, until connected.
    if(!tfmP.sendCommand(SET_FRAME_RATE, 0)) {
      Serial.println("Failed to zero framerate");
      tfmP.printStatus(false);
    } else {
      sampleRate = 0;
    }
  }
  
  if (!deviceConnected && oldDeviceConnected) {
    onDisconnect();
    return;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
    onConnect();
    return;
  }

  if (deviceConnected && millis() > lastSample + SAMPLE_RATE_HZ) {
    loopTFMP();
    lastSample += (1000 / SAMPLE_RATE_HZ);
  }
}
