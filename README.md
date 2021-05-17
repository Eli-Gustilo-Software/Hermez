HERMEZero config library
Version 0.5.0 (Beta), License MIT

Hermez is an application communications library that allows iOS, Android and Mac devices on the same local network to easily and automatically communicate with each other. Under the hood Hermez uses zero configuration to facilitate communications between different types of devices (see, Zero-configuration networking - Wikipedia).

If you need the Android version click here. <https://github.com/Eli-Gustilo-Software/Hermez>

If you need the iOS version click here. <https://github.com/Eli-Gustilo-Software/Hermez-iOS>

Install on Android

Simply add the one file Hermez to your application. It is an object that will need to be instantiated. MainActivity is an ideal place to do this.
https://github.com/Eli-Gustilo-Software/Hermez/tree/main/app/src/main/java/com/eligustilo/hermez

How To Use on Android

Hermez implements 2 custom classes. One, HermezDevice representing a device found on the local network.
data class HermezDevice(
        val name: String
)

Two, HermezMessage representing a message received from another device on the local network. Specifically, these classes are defined as follows.
data class HermezMessage(
        val message: String,
        val json: String,
        val messageID: String,
        val receivingDevice: HermezDevice,
        val sendingDevice: HermezDevice)

There are 5 steps to using Hermez.
One, set the service name used to create a common communication service for all devices. All devices that use this service on the same network will be automatically discovered by Hermez.
	val hermez = Hermez(context: myContext, serviceType: myServiceName)
	
Two, set a unique device name for each device. The device name is used to uniquely identify a specific device on the network.
	hermez.setDeviceName(deviceName: myDeviceName)
	
Three, query for all devices on the network.
	hermez.findAvailableDevices()
	
Then listen for the callback of available devices  (by implementing the HermezCommProtocol interface).

override fun devicesFound(deviceList: ArrayList<HermezDevice> ) {	
	Log.d(TAG, "devices found array list = $deviceList")
	}
	
Four, send messages.

	hermez.sendMessageToDevices(
	message: yourMessage,
	json: yourJsonAsString, 
	messageID: yourMessageID, 
	devices: yourArrayOfDevices)

Five, listen for messages (by implementing the HermezCommProtocol interface).
	override fun messageReceived(message: messageReceived) {
	// handle message received of type HermezMessage
}
