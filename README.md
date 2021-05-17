HERMEZero config library
Version 0.5.0 (Beta), License MIT

Hermez is an application communications library that allows iOS, Android and Mac devices on the same local network to easily and automatically communicate with each other. Under the hood Hermez uses zero configuration to facilitate communications between different types of devices (see, Zero-configuration networking - Wikipedia).

If you need the Android version click here. <https://github.com/Eli-Gustilo-Software/Hermez>

If you need the iOS version click here. <https://github.com/Eli-Gustilo-Software/Hermez-iOS>

Install on Android
Simply add the one file Hermez to your application. It is an object that will need to be instantiated. MainActivity is an ideal place to do this.
https://github.com/Eli-Gustilo-Software/Hermez/tree/main/app/src/main/java/com/eligustilo/hermez

Install on iOS/Mac
<Add instructions>
  
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
override fun devicesFound(deviceList: 
ArrayList<HermezDevice> ) {	
Log.d(TAG, "devices found array list = $deviceList")
	}
Four, send messages.
	hermez.sendMessageToDevices(message: yourMessage, 
json: yourJsonAsString, 
messageID: yourMessageID, 
devices: yourArrayOfDevices)

Five, listen for messages (by implementing the HermezCommProtocol interface).
	override fun messageReceived(message: messageReceived) {
	// handle message received of type HermezMessage
}
How To Use on iOS/Mac
Hermez implements 2 custom structs. One, HermezDevice representing a device found on the local network.
public struct HermezDevice: Codable, Equatable {
	public var name: String
}


Two, HermezMessage representing a message received from another device on the local network. Specifically, these classes are defined as follows.
public struct HermezMessage: Codable, Equatable {
	public var message: String?
	public var jsonData: String?
	public var messageID: String
	public var receivingDevice: HermezDevice
	public var sendingDevice: HermezDevice
}

There are 4 steps to using Hermez.
One, set the service and device names. The service name is used to create a common communication service for all devices. All devices that use this service on the same network will be automatically discovered by Hermez. The device name is used to uniquely identify a specific device on the network.
	Hermez.shared.setServiceAndDeviceName(serviceName: yourServiceName, 
deviceName: yourDeviceName)


Two, find available devices. There are three options for doing this. Option one, use the standard swift protocol/callback pattern by querying for all devices on the network.
	Hermez.shared.findAvailableDevices()
	Then listen for the callback of available devices  (by implementing the HermezCommProtocol protocol).
func availableDevices(devices: [HermesDevice]) {	
print("devices found array list = \(devices)")
	}
	Option two, implement an RxSwift observable. For example,
	HermezWithRx.shared.findDevicesObservable.subscribe { event in
		if let devices = event.element {
			// handle found devices
		}
     	}.disposed(by: disposeBag)


	Option three, implement a Combine observable. For example,
	self.devicesCancellable =  HermezWithCombine.shared.devicesObservable
      	.removeDuplicates()
            .sink { devices in
                	// handle found devices
            }


Three, send messages. 
	Hermez.shared.sendMessageToDevices(message: yourMessage, 
json: yourJsonAsString, 
messageID: yourMessageID, 
devices: yourArrayOfDevices)


Four, listen for messages from other devices on the network.  There are three options for doing this. Option one, use the standard swift protocol/callback pattern (by implementing the HermezCommProtocol protocol).
	override func messageReceived(message: messageReceived) {
	// handle message received of type HermezMessage
}

Option two, implement an RxSwift observable. For example,
	HermezWithRx.shared.messagesObservable.subscribe { messageEvent in
      	if let message = messageEvent.element {
//handle message            	
}
        	}.disposed(by: disposeBag)


	Option three, implement a Combine observable. For example,
	self.messageCancellable =  HermezWithCombine.shared.messageObservable
           .sink { message in
      	if !self.data.contains(message) {
			// handle message
            }

TODO
Support basic security.
Support streaming data (files, videos, audio)
Improve unit tests
Encrypt all messages.
Improve performance.
Add support for Windows and Web.

