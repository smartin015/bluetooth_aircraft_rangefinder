#include <TFMPlus.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

TFMPlus tfmP;
BLEServer *pServer = NULL;
BLECharacteristic * pDistCharacteristic;
BLE2902 * pDistCCCD;
BLECharacteristic * pFluxCharacteristic;
BLECharacteristic * pTempCharacteristic;
BLECharacteristic * pStatusCharacteristic;
bool deviceConnected = false;
bool oldDeviceConnected = false;
uint16_t tfDist;       // Distance measurement in centimeters (default)
uint16_t tfFlux;       // Luminous flux or intensity of return signal
uint16_t tfTemp;       // Temperature in degrees Centigrade (coded)
uint16_t loopCount;    // Loop counter (1-20)
uint64_t lastSample = 0;

#define SERVICE_UUID           "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_DIST "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_FLUX "6E400004-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_TEMP "6E400005-B5A3-F393-E0A9-E50E24DCCA9E"
#define CHARACTERISTIC_UUID_STATUS "6E400006-B5A3-F393-E0A9-E50E24DCCA9E"

#define SAMPLE_RATE_HZ 10
#define TEMP_FLUX_REPORT_PD 20

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
  Serial1.begin(115200, SERIAL_8N1, 4, 15); //Baud rate, parity mode, RX, TX
  delay(20); // Give port time to initalize
  tfmP.begin( &Serial1);

  printTFMPFirmwareVersion();
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
  
  if (deviceConnected && !oldDeviceConnected) { // Connect
    onConnect();
    return;
  }

  if (deviceConnected && millis() > lastSample + SAMPLE_RATE_HZ) {
    loopTFMP();
    lastSample += (1000 / SAMPLE_RATE_HZ);
  }
}
