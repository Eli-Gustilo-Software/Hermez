package com.eligustilo.main

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eligustilo.hermez.Hermez
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

/**
 * A simple [Fragment] test class
 */
class TestFragment() : Fragment(), Hermez.HermezDataInterface {
    private val fragTag = "SecondFrag"
    private lateinit var hermez : Hermez
    private var devices = ArrayList<Hermez.HermezDevice>()
    private var messages = ArrayList<Hermez.HermezMessage>()
    private lateinit var messagessRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var deviceNameTF: TextView
    private var messagesRecyclerAdapter: MessagesRecyclerAdapater? = null

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_second, container, false)
        messagessRecyclerView = root.findViewById(R.id.message_list)

        if (messagesRecyclerAdapter == null) { // create it
            messagesRecyclerAdapter = MessagesRecyclerAdapater(this.messages)
            messagessRecyclerView.layoutManager = LinearLayoutManager(this.activity)
            messagessRecyclerView.adapter = messagesRecyclerAdapter
            messagesRecyclerAdapter!!.notifyDataSetChanged()
        } else {
            //it is created and we need to update or sync data.
            messagesRecyclerAdapter?.updateMessages(this.messages)
        }

        this.messageEditText = root.findViewById(R.id.add_message)
        this.deviceNameTF = root.findViewById(R.id.device_name)

        // Setup Hermez type and delegate
        hermez = Hermez(requireContext(), "_blah._tcp.")
        hermez.initWithDelegate(this)

        val deviceName = checkForName()
        if(deviceName != null) {
            this.deviceNameTF.text = deviceName
        } else {
            val alert: AlertDialog.Builder = AlertDialog.Builder(this.requireContext())
            val edittext = EditText(this.requireActivity())
            alert.setTitle("Set Device Name")
            alert.setView(edittext)
            alert.setPositiveButton("Set Name") { _, _ -> //What ever you want to do with the value
                val newDeviceName = edittext.text.toString()
                val sharedPref = context?.getSharedPreferences("boh_test", 0)
                val prefsEditor = sharedPref?.edit()
                prefsEditor?.putString("device_name", newDeviceName)
                prefsEditor?.apply()
                hermez = Hermez(requireContext(), "_blah._tcp.")
                hermez.initWithDelegate(this)
                GlobalScope.launch(Dispatchers.Main) { // launch new coroutine in background and continue
                    delay(1000) // non-blocking delay for 1 second (default time unit is ms)
                    setupHermez()
                }
            }
            alert.show()
        }
        return root
    }

    private fun checkForName() : String? {
        val sharedPref = context?.getSharedPreferences("boh_test", 0)
        return sharedPref?.getString("device_name", null)
    }

    private fun setupHermez() {
        val deviceName = checkForName()
        if(deviceName != null) {
            this.deviceNameTF.text = deviceName
            hermez.setDeviceName(deviceName)

            messageEditText.setOnKeyListener { view, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    val editText = view as EditText
                    Log.d(fragTag, "KeyEvent.KEYCODE_ENTER message entered = ${editText.text}")
                    if (editText.text.toString().isNotEmpty()) {
                        val messageID = UUID.randomUUID().toString()
                        hermez.sendMessageToDevices(editText.text.toString(), "", messageID, devices)
                        editText.text.clear()

                        // Forces keyboard to hide
                        editText.isFocusableInTouchMode = false
                        editText.isFocusable = false
                        editText.isFocusableInTouchMode = true
                        editText.isFocusable = true
                        val imm =
                            context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
                        imm?.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                    }
                    true
                } else {
                    false
                }
            }
        }
        messageEditText.setOnEditorActionListener {view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val editText = view as EditText
                Log.d(fragTag, "EditorInfo.IME_ACTION_DONE message entered = ${editText.text}")
                if(editText.text.toString().length > 0) {
                    val messageID = UUID.randomUUID().toString()
                    hermez.sendMessageToDevices(editText.text.toString(), "", messageID, devices)
                    editText.text.clear()

                    // Forces keyboard to hide
                    editText.isFocusableInTouchMode = false
                    editText.isFocusable = false
                    editText.isFocusableInTouchMode = true
                    editText.isFocusable = true
                    val imm = context?.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm?.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
                }
                true
            } else {
                false
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(checkForName() != null) {
            setupHermez()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hermez.resetService()
    }

    override fun serviceStarted(serviceType: String, serviceName: String) {
        Log.d(fragTag, "serviceStarted type: $serviceType, serviceName: $serviceName")
        hermez.findAvailableDevices()
    }

    override fun serviceStopped(serviceType: String, serviceName: String) {
        Log.d(fragTag, "serviceStopped: $serviceName")
    }

    override fun messageReceived(hermezMessage: Hermez.HermezMessage) {
        Log.d(fragTag, "Message received: $hermezMessage")
        if(!this.messages.contains(hermezMessage)) {
            this.messages.add(0, hermezMessage)
        }
        if(activity != null) {
            this.requireActivity().runOnUiThread {
                Log.d(fragTag, "messages to be shown are ${this.messages}")
                messagesRecyclerAdapter?.updateMessages(this.messages)
            }
        }
    }

    override fun serviceFailed(serviceType: String, serviceName: String?, error: Hermez.HermezError) {
        Log.d(fragTag, "serviceFailed: $serviceName")
    }

    override fun messageCannotBeSentToDevices(hermezMessage: Hermez.HermezMessage, error: Hermez.HermezError) {
        Log.d(fragTag, "messageCannotBeSentToDevices: $hermezMessage")
    }

    override fun devicesFound(deviceList: ArrayList<Hermez.HermezDevice>) {
        Log.d(fragTag, "deviceList: $deviceList")
        devices = deviceList
    }

    override fun resolveFailed(serviceType: String, serviceName: String, error: Hermez.HermezError) {
        Log.d(fragTag, "resolveFailed serviceName: $serviceName")
    }
}