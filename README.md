# ![Hermez Logo](/hermez.png)
Version 0.5.0 (Beta), License MIT

Hermez is an application communications library that allows iOS, Android and Mac devices on the same local network to easily and automatically communicate with each other. Under the hood Hermez uses zero configuration to facilitate communications between different types of devices (see, Zero-configuration networking - Wikipedia).

- If you need the Android version click here. <https://github.com/Eli-Gustilo-Software/Hermez>

- If you need the iOS version click here. <https://github.com/Eli-Gustilo-Software/Hermez-iOS>

**Install on Android**

Simply add the one file Hermez to your application. It is an object that will need to be instantiated. MainActivity is an ideal place to do this.
https://github.com/Eli-Gustilo-Software/Hermez/tree/main/app/src/main/java/com/eligustilo/hermez

**How To Use on Android**

Hermez implements 2 custom classes. One, HermezDevice representing a device found on the local network.

```kotlin
data class HermezDevice(
    val name: String
)
```
Two, HermezMessage representing a message received from another device on the local network. Specifically, these classes are defined as follows.
```kotlin
data class HermezMessage(
    val message: String,
    val json: String,
    val messageID: String,
    val receivingDevice: HermezDevice,
    val sendingDevice: HermezDevice)
```
There are 5 steps to using Hermez.

1. Set the service name used to create a common communication service for all devices. All devices that use this service on the same network will be automatically discovered by Hermez.
```kotlin
val hermez = Hermez(context: myContext, serviceType: myServiceName)
```
	
2. Set a unique device name for each device. The device name is used to uniquely identify a specific device on the network.
```kotlin
hermez.setDeviceName(deviceName: myDeviceName)
```
	
3. Query for all devices on the network.
```kotlin
hermez.findAvailableDevices()
```
Then listen for the callback of available devices  (by implementing the HermezCommProtocol interface).
```kotlin
override fun devicesFound(deviceList: ArrayList<HermezDevice> ) {	
    Log.d(TAG, "devices found array list = $deviceList")
}
```
4. Send messages.
```kotlin
hermez.sendMessageToDevices(
    message: yourMessage,
    json: yourJsonAsString, 
    messageID: yourMessageID, 
    devices: yourArrayOfDevices)
```
5. Listen for messages (by implementing the HermezCommProtocol interface).
```kotlin
override fun messageReceived(message: messageReceived) {
    // handle message received of type HermezMessage
}
```
