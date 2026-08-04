package com.example.bluetoothkeyboard

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothProfile.STATE_CONNECTED
import android.bluetooth.BluetoothProfile.STATE_DISCONNECTED
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

class BluetoothHidManager(private val context: Context, private val listener: ConnectionListener) {

    private val TAG = "BluetoothHidManager"

    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothHidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    var isAppRegistered = false
        private set

    interface ConnectionListener {
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onConnectionError(message: String)
        fun onDevicesFound(devices: List<BluetoothDevice>)
        fun onAppRegistered(registered: Boolean)
    }

    private val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.d(TAG, "HID Connection State: $state for ${device?.address}")
            hostDevice = if (state == STATE_CONNECTED) device else null
            listener.onConnectionStateChanged(device, state)
        }

        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            Log.d(TAG, "HID App Registration: $registered")
            isAppRegistered = registered
            listener.onAppRegistered(registered)
        }

        @SuppressLint("MissingPermission")
        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            super.onGetReport(device, type, id, bufferSize)
            Log.d(TAG, "Host probe (GetReport): type=$type id=$id")
            
            if (device != null && bluetoothHidDevice != null) {
                val report = ByteArray(8) { 0 }
                try {
                    bluetoothHidDevice?.replyReport(device, type, id, report)
                } catch (e: Exception) { Log.e(TAG, "ReplyReport failed", e) }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            super.onSetReport(device, type, id, data)
            if (device != null) {
                try {
                    bluetoothHidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
                } catch (e: Exception) { Log.e(TAG, "ReportError failed", e) }
            }
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            super.onVirtualCableUnplug(device)
            Log.d(TAG, "Virtual cable unplugged")
            hostDevice = null
            listener.onConnectionStateChanged(device, STATE_DISCONNECTED)
        }
    }

    private val hidServiceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = proxy as BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = null
                isAppRegistered = false
            }
        }
    }

    private val HID_DESCRIPTOR = byteArrayOf(
        0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(), // Collection (Application)
        0x85.toByte(), 0x01.toByte(), //   Report ID (1)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (Keyboard Left Control)
        0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (Keyboard Right GUI)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x95.toByte(), 0x08.toByte(), //   Report Count (8)
        0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute) - Modifier byte
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x81.toByte(), 0x01.toByte(), //   Input (Constant) - Reserved byte
        0x95.toByte(), 0x05.toByte(), //   Report Count (5)
        0x75.toByte(), 0x01.toByte(), //   Report Size (1)
        0x05.toByte(), 0x08.toByte(), //   Usage Page (LEDs)
        0x19.toByte(), 0x01.toByte(), //   Usage Minimum (Num Lock)
        0x29.toByte(), 0x05.toByte(), //   Usage Maximum (Kana)
        0x91.toByte(), 0x02.toByte(), //   Output (Data, Variable, Absolute) - LED report
        0x95.toByte(), 0x01.toByte(), //   Report Count (1)
        0x75.toByte(), 0x03.toByte(), //   Report Size (3)
        0x91.toByte(), 0x01.toByte(), //   Output (Constant) - LED report padding
        0x95.toByte(), 0x06.toByte(), //   Report Count (6)
        0x75.toByte(), 0x08.toByte(), //   Report Size (8)
        0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(), //   Usage Page (Keyboard/Keypad)
        0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(), //   Input (Data, Array) - Key codes
        0xC0.toByte()                 // End Collection
    )

    var deviceBroadcastName: String = "Logitech MX Keys S"
    var deviceProvider: String = "Logitech Inc."
    var deviceDescription: String = "Logitech Wireless Bluetooth Keyboard"

    fun getSdpSettings(): BluetoothHidDeviceAppSdpSettings {
        return BluetoothHidDeviceAppSdpSettings(
            deviceBroadcastName,
            deviceDescription,
            deviceProvider,
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HID_DESCRIPTOR
        )
    }

    private val callbackExecutor = Executors.newSingleThreadExecutor()

    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported")
        }
    }

    fun startHidDeviceService() {
        if (bluetoothHidDevice != null) return
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter?.getProfileProxy(context, hidServiceListener, BluetoothProfile.HID_DEVICE)
            } else {
                listener.onConnectionError("Bluetooth is turned off")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing", e)
            listener.onConnectionError("Bluetooth permission is required")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopHidDeviceService() {
        bluetoothHidDevice?.let { hidDevice ->
            if (isAppRegistered) {
                try {
                    hidDevice.unregisterApp()
                } catch (e: Exception) { Log.e(TAG, "Unregister error", e) }
            }
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        }
        bluetoothHidDevice = null
        isAppRegistered = false
    }

    fun registerApp() {
        if (isAppRegistered) return
        try {
            bluetoothHidDevice?.registerApp(
                getSdpSettings(), null, null,
                callbackExecutor,
                hidDeviceCallback
            )
        } catch (e: Exception) {
            Log.e(TAG, "Registration error", e)
            listener.onConnectionError("Registration Failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun applyKeyboardDisguise(name: String, provider: String = "Logitech Inc.", description: String = "Logitech Wireless Bluetooth Keyboard"): Boolean {
        deviceBroadcastName = name
        deviceProvider = provider
        deviceDescription = description
        
        var nameChanged = false
        try {
            bluetoothAdapter?.name = name
            nameChanged = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set adapter name", e)
        }

        // Re-register HID SDP with new brand details
        if (isAppRegistered && bluetoothHidDevice != null) {
            try {
                bluetoothHidDevice?.unregisterApp()
                isAppRegistered = false
                registerApp()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-register with new identity", e)
            }
        }
        return nameChanged
    }

    @SuppressLint("MissingPermission")
    fun getAdapterName(): String {
        return try {
            bluetoothAdapter?.name ?: ""
        } catch (e: Exception) { "" }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (bluetoothAdapter == null) {
            listener.onConnectionError("Bluetooth unavailable")
            return
        }

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cancel discovery warning: ${e.message}")
        }

        if (bluetoothHidDevice == null) {
            startHidDeviceService()
            listener.onConnectionError("Initializing HID service... Click Connect again")
            return
        }

        if (!isAppRegistered) {
            registerApp()
        }
        
        if (bluetoothHidDevice?.getConnectionState(device) == STATE_CONNECTED) {
            hostDevice = device
            listener.onConnectionStateChanged(device, STATE_CONNECTED)
            return
        }

        try {
            listener.onConnectionStateChanged(device, BluetoothProfile.STATE_CONNECTING)
            val success = bluetoothHidDevice?.connect(device) ?: false
            if (!success) {
                Log.w(TAG, "bluetoothHidDevice.connect returned false")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connect error", e)
            listener.onConnectionError("Connect Failed: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        hostDevice?.let { device ->
            try {
                bluetoothHidDevice?.disconnect(device)
            } catch (e: Exception) { Log.e(TAG, "Disconnect error", e) }
        }
    }

    fun isConnected(): Boolean = hostDevice != null && bluetoothHidDevice != null

    @SuppressLint("MissingPermission")
    fun forceHalt() {
        // Physical buffer flush: Reset the app registration
        if (isAppRegistered) {
            try {
                bluetoothHidDevice?.unregisterApp()
                isAppRegistered = false
                // Re-register to be ready for next session
                registerApp()
            } catch (e: Exception) { Log.e(TAG, "Halt failed", e) }
        }
        
        // Send a direct panic report if still connected
        val device = hostDevice ?: return
        val report = ByteArray(8) { 0 }
        try {
            bluetoothHidDevice?.sendReport(device, 1, report)
        } catch (e: Exception) { Log.e(TAG, "Panic report failed", e) }
    }

    /**
     * Sends a keyboard report directly to the HID device.
     * We've removed all executors and coroutine switches here to ensure
     * that when this function returns, the system has accepted the report.
     */
    @SuppressLint("MissingPermission")
    fun sendKeyboardReport(modifier: Int, keycode: Int): Boolean {
        val device = hostDevice ?: return false
        val report = ByteArray(8).apply {
            this[0] = modifier.toByte()
            this[2] = keycode.toByte()
        }
        return try {
            bluetoothHidDevice?.sendReport(device, 1, report) == true
        } catch (e: Exception) {
            Log.e(TAG, "KB report error", e)
            false
        }
    }
}
