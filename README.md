# bluetooth_aircraft_rangefinder

## Notes

Crappy BT headphones are Classic BT, requires the a2dp_source example (but works!)

### SPIFFS

So, execute "make menuconfig" command and select "Partition Table ---> Partition Table (Factory app, two OTA definitions) ---> ( ) Custom partition table CSV" and provide name of csv file whatever you want to create as per your requirement.

This way you can split 4MB SPI Flash into 2MB+2MB in which first 2 MB (Not exactly as some of KBytes are used for Boot Loader and other configurations) is for your application image while next 2 MB is for you file system which you want.

Uses spiffsgen example to build and flash a directory: https://github.com/espressif/esp-idf/tree/beaefd3359973dcf8bba4e1d4462aa7f5d67be6a/examples/storage/spiffsgen

## Links

* https://www.reddit.com/r/esp32/comments/an8u1t/hacky_messy_notes_on_how_i_got_audio_playing_from/


