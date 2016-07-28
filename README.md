# Luanode USB HOST

This is an Android USB Host app for ESP8266/ESP32. The Android device receive messages, 
sent from ESP8266, via OTG. Then the messages display on this app.

# How to use

* Download this project and build it within `Android Studio`.
* Modify the baud rate defined in `Constants.java` to match your slave device.
* Install app (You can install the `app-debug.apk` we provide in the root dir) and run it.
* Connect ESP8266 to Android device via OTG, and grant permission, then the Android can receive message
