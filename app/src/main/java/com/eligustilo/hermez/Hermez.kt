package com.eligustilo.hermez

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.Exception
import java.net.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.collections.ArrayList

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
* */


const val MESSAGE_TERMINATOR = "\r\n"
//This Class is split into two different inner classes Server and Client
class Hermez(context: Context, serviceType: String) {
    private val tag = "Hermez"
    private var mServiceType: String = serviceType
    private var mServiceName: String? = null
    private var mArrayOfDevicesFound: ArrayList<HermezDevice>? = ArrayList()
    private var mContext = context
    private var mHashtable = Hashtable<String, Socket>()
    private var messageQueue = ArrayList<HermezMessage>()
    private var objectToNotify: HermezDataInterface? = null
    private var hermezService: HermezService? = null
    private var hermezBrowser: HermezBrowser? = null
    private val unknownZeroConfigDevice: HermezDevice = HermezDevice("Unknown Device")
    private var myDeviceName : HermezDevice = HermezDevice(Build.MODEL)
    private val PING: String = "PING"

    init {
        hermezService = HermezService()
        hermezBrowser = HermezBrowser()
    }

    enum class HermezError {
        SERVICE_FAILED, MESSAGE_NOT_SENT, SERVICE_RESOLVE_FAILED
    }

    data class HermezDevice(
        val name: String,
    )

    data class HermezMessage(
        val message: String,
        val json: String,
        val messageID: String,
        val receivingDevice: HermezDevice,
        val sendingDevice: HermezDevice)


    // All devices on the network will create there own zero config service with (1) a common service type and (2) unique service names (for each device)
    interface HermezDataInterface {
        fun serviceStarted(serviceType: String, serviceName: String)
        fun serviceStopped(serviceType: String, serviceName: String)
        fun messageReceived(hermezMessage: HermezMessage)
        fun serviceFailed(serviceType: String, serviceName: String?, error: HermezError)
        fun messageCannotBeSentToDevices(hermezMessage: HermezMessage, error: HermezError)
        fun devicesFound(deviceList: ArrayList<HermezDevice>)
        fun resolveFailed(serviceType: String, serviceName: String, error: HermezError)
    }

    fun setServiceName(serviceType: String) {
        // will be used to set service type, for example, "_<serviceName>._tcp" can be used to change it in case user wants multiple serviceTypes.
        mServiceType = serviceType
        if (mServiceName != null) {
            HermezService().registerService()
        } else {
            Log.e(tag, "This ZeroConfig Library requires a correct serviceType and a deviceName")
        }
    }

    fun setDeviceName(deviceName: String) {
        // will be used to set unique name of device
        mServiceName = deviceName
        myDeviceName = HermezDevice(deviceName)
        if (mServiceName != null) {
            HermezService().registerService()
        } else {
            Log.e(tag, "This ZeroConfig Library requires a correct serviceType and a deviceName")
        }
    }

    fun findAvailableDevices() {
        //this activates the search feature. In conjunction with the setDeviceName and/or setServiceName will activate Hermez.
        GlobalScope.launch { // launch new coroutine in background and continue
            delay(50) // non-blocking delay for 1 second (default time unit is ms)
            hermezBrowser?.discoverService()
            runMessageQueue()
        }
    }

    fun sendMessageToDevices(message: String, json: String, messageID: String, devices: ArrayList<HermezDevice>) {
        // will send message to devices (see interface on receiving message)
        // messageID is a unique identifier for that message
        for (device in devices){
            val zConfigDevice  = HermezDevice(device.name)
            val mMessage = HermezMessage(message, json, messageID, zConfigDevice, myDeviceName)
            Log.d(tag, "new message in queue= $mMessage")
            if (!messageQueue.contains(mMessage)){
                messageQueue.add(mMessage) //this queue automatically sends its messages if possible. See clearMessageQueue
            }
        }
    }

    fun resetService(){
        //HARD reset
        //While hopefully unnecessary it may be desirable to be able to throw the whole process away and begin again.
        hermezService?.resetRegistration()
        Thread.sleep(500) //maybe we want to wait a second for registration to go
        hermezBrowser?.resetDiscovery()
    }

    fun resetDiscovery(){
        //SOFT reset
        //While hopefully unnecessary it may be desirable to be able to throw just the most unstable part. Discovery.
        hermezBrowser?.resetDiscovery()
    }

    fun cleanup(){
        //Needs to be called OnPause and OnDestroy
        HermezService().cleanup()
        HermezBrowser().cleanup()
    }


    fun initWithDelegate(delegate: HermezDataInterface? = null) {
        //this sets the object/activity/fragment to receive data from Hermez. This is required.
        this.objectToNotify = delegate
    }

    private fun runMessageQueue(){
        Thread{//async
            while (true) {//while(true) will always run
                if (messageQueue.isNotEmpty()){
                    //send the message if possible
                    val instanceOfMessageQueue = ArrayList(messageQueue)
                    for (message in instanceOfMessageQueue){
                        if (mHashtable.containsKey(message.receivingDevice.name)){
                            val socket = mHashtable.get(message.receivingDevice.name)
                            if (socket != null) {
                                val runnable = hermezBrowser?.ClientWriter(message, socket)
                                val writerThread = Thread(runnable)
                                writerThread.start()
                            }
                        }
                    }
                }
                Thread.sleep(2000)
            }
        }.start()
    }

    //SERVER CODE (Registration)
    private inner class HermezService {
        private var localPortServer = 101
        private var nsdManagerServer: NsdManager? = null
        private var connectedClientsServer: MutableList<Socket> = CopyOnWriteArrayList()

        private fun initializeServerSocket() {
            // Initialize a server socket on the next available port.
            val serverSocket = ServerSocket(0).also { socket ->
                // Store the chosen port.
                localPortServer = socket.localPort
            }
            Thread {
                //serverSocket != null
                while (true) {
                    try {
                        Log.d(tag, "serverSocket set to accept on ${serverSocket.localPort}")
                        serverSocket.accept()?.let {
                            Log.d("ServerSocket", "accepted client")
                            Log.d(tag, "new IT Socket = ${it.port}")
                            // Hold on to the client socket
                            connectedClientsServer.add(it)

                            // Start reading messages
                            Thread(ServiceReader(it)).start()
                            //NOTE you can have broadcast a message back to the discoverer here. Would need to create a reader for Client.
                        }
                    } catch (e: SocketException) {
                        break
                    }
                }
            }.start()
        }

        fun unregisterService() {
            nsdManagerServer?.unregisterService(registrationListener)
            nsdManagerServer = null
        }

        fun registerService() {
            initializeServerSocket()
            val serviceInfo = NsdServiceInfo().apply {
                // The name is subject to change based on conflicts
                // with other services advertised on the same network.
                serviceName = mServiceName
                serviceType = mServiceType
                port = localPortServer
            }
            nsdManagerServer = (mContext.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
                registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            }
        }

        fun resetRegistration(){
            unregisterService()
            GlobalScope.launch { // launch new coroutine in background and continue
                delay(3000) // non-blocking delay for 1 second (default time unit is ms)
                registerService()
            }
        }

        fun cleanup(){
            nsdManagerServer?.unregisterService(registrationListener)
        }

        private val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                // Save the service name. Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                val registrationListenerServiceName = NsdServiceInfo.serviceName
                val registrationListenerServicePort = NsdServiceInfo.port
                val type = NsdServiceInfo.serviceType
                objectToNotify?.serviceStarted(mServiceType, mServiceName!!)
                Log.d(tag, "It worked! Service is registered! Service name is $registrationListenerServiceName Service port is $registrationListenerServicePort and Service type is $type")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Registration failed! Put debugging code here to determine why.
                Log.e(tag, "Registration failed. ServiceInfo = $serviceInfo and error code = $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                // Service has been unregistered. This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
                Log.i(tag, "yourService: $arg0 has been unregistered.")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Unregistration failed. Put debugging code here to determine why.
                Log.e(tag, "Unregistration failed. ServiceInfo = $serviceInfo and error code = $errorCode")
            }
        }

        private inner class ServiceReader(private val client: Socket) : Runnable {
            private val tag = "ServiceReader"
            override fun run() {
                var line: String?
                val reader: BufferedReader

                //FIRST create a BufferedReader only once

                try {
                    reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    Log.w(tag, "BufferedReader got hit")
                } catch (e: IOException) {
                    Log.w(tag, "BufferedReader failed to initialize")
                    connectedClientsServer.remove(client)
                    return
                }

                while (true) { //aka always

                    //SECOND try and read normally

                    try {
                        line = reader.readLine()
                        Log.d(tag, "line read is $line")
                        if (line == null) { //if the line is null then remove. Often is caused by losing socket.
                            connectedClientsServer.remove(client)
                            break
                        } else { //else the line actually exists and we should try and read it.
                            val message = Gson().fromJson(line, HermezMessage::class.java) //try and read it.
                            Log.d(tag, "parsed message is $message")
                            if (message != null){

                                //THIRD if we get a message tell the sender we got it .

                                //PING is also meant to ensure device is valid on network. Fix Google's flakiness
                                //we send a ping to anyone who sends us a message. They will validate that you exist. ClientWriter will validate.
                                val pingMessage = HermezMessage(PING, "", message.messageID, message.sendingDevice, message.receivingDevice)
                                Log.d(tag, "message received from device: ${message.sendingDevice.name}, mHashtable: $mHashtable")

                                if(!mHashtable.containsKey(message.sendingDevice.name)) { //if we don't have the message senders name then we need to restart discovery.
                                    hermezBrowser?.resetDiscovery(false)
                                }
                                val writer: PrintWriter
                                try {
                                    writer = PrintWriter(client.getOutputStream())
                                    val jsonString = Gson().toJson(pingMessage)
                                    Log.d(tag, "jsonString is $jsonString")
                                    writer.print(jsonString + MESSAGE_TERMINATOR)
                                    writer.flush()
                                } catch (e: IOException) {
                                    // If the writer fails to initialize there was an io problem, close your connection
                                    client.close()
                                    objectToNotify?.messageCannotBeSentToDevices(pingMessage, HermezError.MESSAGE_NOT_SENT)
                                }


                                //FOURTH give message to ui in a synchronized manner.
                                synchronized(objectToNotify!!) {
                                    objectToNotify?.messageReceived(message)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        connectedClientsServer.remove(client)
                        break
                    }
                }
            }
        }
    }

    //CLIENT CODE
    private inner class HermezBrowser {
        private var nsdManagerClient: NsdManager? = null
        private var multicastLock: WifiManager.MulticastLock? = null
        private var discoverListenerInUse: Boolean = false

        fun discoverService() {
            //Multicast is required to see all devices but consumes power, we turn it off as soon as searching is done. todo we should test and ensure it is actually off. We almost constantly search...
            multicastLock = (mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).createMulticastLock(tag)
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            nsdManagerClient = mContext.getSystemService(Context.NSD_SERVICE) as NsdManager

            when (discoverListenerInUse) {
                false -> {
                    discoverListenerInUse = true
                    nsdManagerClient!!.discoverServices(mServiceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                }
                true -> {
                    Log.e(tag, "Only need to search for devices once. The Library will give you an updated array list of all found devices with serviceType")
                }
            }
        }

        fun stopDiscovery(clearHashtable: Boolean = true) {
            nsdManagerClient?.stopServiceDiscovery(discoveryListener)
            nsdManagerClient = null
            discoverListenerInUse = false
            if(clearHashtable) {//default is true
                mHashtable.clear()
            }
        }

        fun resetDiscovery(clearHashtable: Boolean = true){ //this is a Kotlin feature that allows me to be passed in a boolean check when called. true == default
            stopDiscovery(clearHashtable)
            GlobalScope.launch { // launch new coroutine in background and continue
                delay(3000) // non-blocking delay for 1 second (default time unit is ms)
                discoverService()
            }
        }

        fun cleanup(){
            nsdManagerClient?.stopServiceDiscovery(discoveryListener)
        }

        private val discoveryListener = object : DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(tag, "Service discovery started. Attempting to find serviceType.")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found! Do something with it.
                Log.d(tag, "Service discovery success $service found")
                val nameOfServiceFound = service.serviceName
                if(!mHashtable.containsKey(nameOfServiceFound)) {
                    nsdManagerClient?.resolveService(service, MyResolveListener())
                    multicastLock?.release()
                    multicastLock = null
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(tag, "service lost: ${service.serviceName}")
                multicastLock?.release()
                multicastLock = null
                objectToNotify?.serviceFailed(mServiceType, (service.serviceName), HermezError.SERVICE_FAILED)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(tag, "Discovery stopped: $serviceType")
                multicastLock?.release()
                multicastLock = null
                objectToNotify?.serviceStopped(mServiceType, (unknownZeroConfigDevice.name))
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery failed: Error code:$errorCode")
                multicastLock?.release()
                multicastLock = null
                objectToNotify?.serviceFailed(mServiceType, (unknownZeroConfigDevice.name), HermezError.SERVICE_FAILED)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(tag, "Discovery failed: Error code:$errorCode")
                multicastLock?.release()
                multicastLock = null
                objectToNotify?.serviceFailed(mServiceType, (unknownZeroConfigDevice.name), HermezError.SERVICE_FAILED)
            }
        }

        private inner class MyResolveListener : NsdManager.ResolveListener {
            private val tag = "resolveListener"

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Called when the resolve fails. Use the error code to debug.
                Log.e(tag, "Resolve failed: $errorCode")
                objectToNotify?.resolveFailed(mServiceType, serviceInfo.serviceName, HermezError.SERVICE_RESOLVE_FAILED)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "Resolve Succeeded. $serviceInfo")
                try {
                    // Connect to the host
                    val localPortClient = Socket(serviceInfo.host, serviceInfo.port)

                    mHashtable[serviceInfo.serviceName] = localPortClient
                    Log.d(tag, "mHashtable: $mHashtable")
                    mArrayOfDevicesFound?.clear()
                    for (item in mHashtable){
                        mArrayOfDevicesFound?.add(HermezDevice(item.key))
                    }
                    if (objectToNotify != null && mArrayOfDevicesFound != null){
                        objectToNotify!!.devicesFound(mArrayOfDevicesFound!!)
                    }
                    //Log.d(tag, "mHashtable is filled with ${serviceInfo.serviceName} and $localPortClient")
                    //Log.d(tag, "connection state = ${localPortClient.isConnected}")
                    val newMessage = HermezMessage("NSDClientClass did connect to service name ${serviceInfo.serviceName}", "", "0", HermezDevice(serviceInfo.serviceName), myDeviceName)
                    val runnable = hermezBrowser?.ClientWriter(newMessage, localPortClient)
                    val writerThread = Thread(runnable)
                    writerThread.start()
                } catch (e: UnknownHostException) {
                    Log.e(tag, "Unknown host. ${e.localizedMessage}")
                    objectToNotify?.resolveFailed(serviceInfo.serviceType, serviceInfo.serviceName, HermezError.SERVICE_RESOLVE_FAILED)
                } catch (e: ConnectException) {
                    Log.e(tag, e.localizedMessage)
                    objectToNotify?.resolveFailed(serviceInfo.serviceType, serviceInfo.serviceName, HermezError.SERVICE_RESOLVE_FAILED)

                }
            }
        }

        inner class ClientWriter(val hermezMessage: HermezMessage, val socket: Socket) : Runnable {
            private val tag = "ClientWriter"
            override fun run() {
                val writer: PrintWriter

                //FIRST send the message.
                try {
                    writer = PrintWriter(socket.getOutputStream())
                    val jsonString = Gson().toJson(hermezMessage)
                    Log.d(tag, "jsonString is $jsonString")
                    writer.print(jsonString + MESSAGE_TERMINATOR)
                    writer.flush()

                    //SECOND begin a countdown as we await their PING confirmation

                    val startTime = System.currentTimeMillis() //fetch starting time
                    var timedOut = false
                    val inputStreamSocket = socket.getInputStream()
                    while(inputStreamSocket.available() == 0 && !timedOut){//while there is no data and we are not timed out we wait for the ping for x seconds.
                        Thread.sleep(1000) //this is meant to put the thread to sleep and only check once a second the if check. So 10 checks total.
                        if (System.currentTimeMillis() - startTime > 10000) {//if waiting ping takes too long then we are timeout.
                            timedOut = true
                        }
                    }
                    if(timedOut) { //we timed out on receiving the ping so delete them and re-find.
                        Log.d(tag, "We timedOut: Handle Ping error!")
                        val removeDevice = hermezMessage.receivingDevice
                        if (mHashtable.containsKey(removeDevice.name)) {
                            mHashtable.remove(removeDevice.name)
                            resetDiscovery(false)//
                        }

                        //THIRD we most likely got a PING but lets check.

                    }else{//we are not timeOut and we got a message
                        val line: String?
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        line = reader.readLine() //read the message
                        if (line == null) { //device is available but no data is given. This should never be called.
                            Log.d(tag, "This should never be called. If it is......good luck.")
                            //there was no ping and we need to reacquire
                        } else {
                            //we almost certainly got a ping and we are good. Nothing else should ever be sent to this other than a PING
                            val message = Gson().fromJson(line, HermezMessage::class.java)
                            if (message != null){
                                if (message.message == "PING"  ){//we got pinged in response to the message we sent.
                                    Log.d(tag, "parsed message is $message and should be ping")
                                    messageQueue.remove(hermezMessage)

                                }else{//we got a message but not a ping. We should never call this.
                                    Log.d(tag, "parsed message is $message....This should never be called.")
                                }
                            } else {
                                Log.e(tag, "ERROR: Message is null and should not be!")
                            }
                        }
                    }
                } catch (e: IOException) {
                    // If the writer fails to initialize there was an io problem, close your connection
                    socket.close()
                    objectToNotify?.messageCannotBeSentToDevices(hermezMessage, HermezError.MESSAGE_NOT_SENT)
                } catch (e: Exception) {
                    Log.d(tag, "error = ${e.localizedMessage}")
                }
            }//runnable close when fun run() ends.
        }
    }
}


