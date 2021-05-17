# Hermez

Hermez is a library designed to utilized NSD/Bonjour to send what is effectively string files between devices. Currently Android and Apple are the only opperating systems supported as of 5-17-21. Hermez is a lightweight easy to use library. Hermez is a single file contained within this repo and requires minimal other libraries to work. This repo contains an easy test activity that can demonstrate Hermez cababilities but that activity is NOT required to use Hermez. There is an additional example application I built, The Bag of Holding, which is a more complex implementation of the Hermez Library.

Please note: This is currently the Alpha release of this library and may have unknown bugs or instability issues. Please feel free to improve upon this code.

API USAGE
/*
* This Library is setup to transfer data from a 'Discoverer' to a 'Broadcaster'/'Registerer'
* It accomplishes this by utilizing ZeroConfig.
*
* API Usage: (Across Android and Swift devices)
* 1) Must instantiate the Library: Providing Context and a serviceType such as _myexampletype._tcp
* 2) In order to be successfully found you must call setDeviceName and provide the Library a unique deviceName. (Upon setting the name your phone will broadcast itself as serviceType with serviceName) (Note you can change serviceType)
* 3) Your app can call findDevices to obtain a ArrayList of devices on the local WiFi network utilizing your _myexampletype._tcp serviceType.
* 4) Your app may then send a Message Object to any unique deviceName sharing your unique serviceType. Example: mMyMessage = ("Name", "ParsableMessage", "UniqueMessageID", deviceNameToSendTo)
* 5) When you send a message your app 'discovers' all nearby devices with _myexampletype._tcp and then looks for deviceNameToSendTo and relays your message ASynchronously
* 6) It is highly recommended to call cleanup upon app closure.
* */
