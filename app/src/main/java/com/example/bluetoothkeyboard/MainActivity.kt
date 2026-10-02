package com.example.bluetoothkeyboard

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity(), TypingService.ProgressListener {

    private val TAG = "MainActivity"

    private lateinit var statusDot: View
    private lateinit var connectionStatusTextView: TextView
    private lateinit var deviceAutoCompleteTextView: AutoCompleteTextView
    private lateinit var textInputEditText: TextInputEditText
    private lateinit var charCountTextView: TextView

    private lateinit var btnProBadge: MaterialButton
    private lateinit var btnCodeVault: MaterialButton
    private lateinit var btnPasteClipboard: MaterialButton
    private lateinit var btnSanitizeCode: MaterialButton
    private lateinit var btnClearText: MaterialButton
    private lateinit var btnSampleText1: MaterialButton
    private lateinit var btnSampleText2: MaterialButton
    private lateinit var btnSampleText3: MaterialButton
    private lateinit var smartIdeModeCheckBox: MaterialCheckBox
    private lateinit var autoClearCheckBox: MaterialCheckBox

    private lateinit var profileGhostChip: Chip
    private lateinit var profileStudentChip: Chip
    private lateinit var profileExpertChip: Chip

    private lateinit var chip60Wpm: Chip
    private lateinit var chip150Wpm: Chip
    private lateinit var chip300Wpm: Chip
    private lateinit var chip600Wpm: Chip
    private lateinit var chip1200Wpm: Chip
    private lateinit var chip3000Wpm: Chip
    private lateinit var chipInstant: Chip

    private lateinit var speedSlider: Slider
    private lateinit var speedLabel: TextView
    private lateinit var countdownDelayCheckBox: MaterialCheckBox
    private lateinit var thinkingPausesCheckBox: MaterialCheckBox
    private lateinit var randomDelayCheckBox: MaterialCheckBox
    private lateinit var jitterSlider: RangeSlider
    private lateinit var jitterLabel: TextView
    private lateinit var humanizeCheckBox: MaterialCheckBox
    private lateinit var fluctuationSlider: Slider
    private lateinit var fluctuationLabel: TextView
    private lateinit var typoSlider: Slider
    private lateinit var typoLabel: TextView

    private lateinit var startTypingButton: MaterialButton
    private lateinit var stopTypingButton: MaterialButton
    private lateinit var typingProgressTextView: TextView
    private lateinit var typingProgressBar: LinearProgressIndicator
    private lateinit var connectButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton
    private lateinit var btnMakeDiscoverable: MaterialButton
    private lateinit var btnAudioFix: MaterialButton
    private lateinit var tvActiveIdentity: TextView
    private lateinit var btnChangeIdentity: MaterialButton

    private lateinit var liveWpmValueTextView: TextView
    private lateinit var liveSpeedBadge: TextView
    private lateinit var charsPerSecTextView: TextView
    private lateinit var avgWpmTextView: TextView
    private lateinit var elapsedTimeTextView: TextView
    private lateinit var layoutLiveTelemetry: View

    private lateinit var cardConnection: View
    private lateinit var cardTextConsole: View
    private lateinit var cardEngineParams: View
    private lateinit var cardExecution: View

    private var typingService: TypingService? = null
    private var isBound = false
    
    private var selectedBluetoothDevice: BluetoothDevice? = null
    private var pairedDevices: List<BluetoothDevice> = emptyList()

    private var countdownTimer: CountDownTimer? = null
    private var isCountingDown = false
    private lateinit var snippetVaultManager: SnippetVaultManager

    private val REQUEST_BLUETOOTH_PERMISSIONS = 1

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TypingService.LocalBinder
            typingService = binder.getService()
            typingService?.setProgressListener(this@MainActivity)
            isBound = true
            updatePairedDevices()
            syncServiceParams()
            applySavedIdentity()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            typingService = null
        }
    }

    private val requestEnableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            typingService?.bluetoothHidManager?.startHidDeviceService()
            updatePairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        snippetVaultManager = SnippetVaultManager(this)

        initViews()
        setupUI()
        animateCardsOnLaunch()
        loadPersistentSettings()
        updateTypingUI(false)
        connectButton.isEnabled = false
        disconnectButton.isEnabled = false
        startTypingButton.isEnabled = false
        checkBluetoothPermissions()
        
        val intent = Intent(this, TypingService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun initViews() {
        statusDot = findViewById(R.id.statusDot)
        btnProBadge = findViewById(R.id.btnProBadge)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        deviceAutoCompleteTextView = findViewById(R.id.deviceSpinner)
        textInputEditText = findViewById(R.id.textInputEditText)
        charCountTextView = findViewById(R.id.charCountTextView)

        btnCodeVault = findViewById(R.id.btnCodeVault)
        btnPasteClipboard = findViewById(R.id.btnPasteClipboard)
        btnSanitizeCode = findViewById(R.id.btnSanitizeCode)
        btnClearText = findViewById(R.id.btnClearText)
        btnSampleText1 = findViewById(R.id.btnSampleText1)
        btnSampleText2 = findViewById(R.id.btnSampleText2)
        btnSampleText3 = findViewById(R.id.btnSampleText3)
        smartIdeModeCheckBox = findViewById(R.id.smartIdeModeCheckBox)
        autoClearCheckBox = findViewById(R.id.autoClearCheckBox)

        profileGhostChip = findViewById(R.id.profileGhostChip)
        profileStudentChip = findViewById(R.id.profileStudentChip)
        profileExpertChip = findViewById(R.id.profileExpertChip)

        chip60Wpm = findViewById(R.id.chip60Wpm)
        chip150Wpm = findViewById(R.id.chip150Wpm)
        chip300Wpm = findViewById(R.id.chip300Wpm)
        chip600Wpm = findViewById(R.id.chip600Wpm)
        chip1200Wpm = findViewById(R.id.chip1200Wpm)
        chip3000Wpm = findViewById(R.id.chip3000Wpm)
        chipInstant = findViewById(R.id.chipInstant)

        speedSlider = findViewById(R.id.speedSlider)
        speedLabel = findViewById(R.id.speedLabel)
        countdownDelayCheckBox = findViewById(R.id.countdownDelayCheckBox)
        thinkingPausesCheckBox = findViewById(R.id.thinkingPausesCheckBox)
        randomDelayCheckBox = findViewById(R.id.randomDelayCheckBox)
        jitterSlider = findViewById(R.id.jitterSlider)
        jitterLabel = findViewById(R.id.jitterLabel)
        humanizeCheckBox = findViewById(R.id.humanizeCheckBox)
        fluctuationSlider = findViewById(R.id.fluctuationSlider)
        fluctuationLabel = findViewById(R.id.fluctuationLabel)
        typoSlider = findViewById(R.id.typoSlider)
        typoLabel = findViewById(R.id.typoLabel)

        liveWpmValueTextView = findViewById(R.id.liveWpmValueTextView)
        liveSpeedBadge = findViewById(R.id.liveSpeedBadge)
        charsPerSecTextView = findViewById(R.id.charsPerSecTextView)
        avgWpmTextView = findViewById(R.id.avgWpmTextView)
        elapsedTimeTextView = findViewById(R.id.elapsedTimeTextView)
        layoutLiveTelemetry = findViewById(R.id.layoutLiveTelemetry)

        startTypingButton = findViewById(R.id.startTypingButton)
        stopTypingButton = findViewById(R.id.stopTypingButton)
        typingProgressTextView = findViewById(R.id.typingProgressTextView)
        typingProgressBar = findViewById(R.id.typingProgressBar)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        btnMakeDiscoverable = findViewById(R.id.btnMakeDiscoverable)
        btnAudioFix = findViewById(R.id.btnAudioFix)
        tvActiveIdentity = findViewById(R.id.tvActiveIdentity)
        btnChangeIdentity = findViewById(R.id.btnChangeIdentity)

        cardConnection = findViewById(R.id.cardConnection)
        cardTextConsole = findViewById(R.id.cardTextConsole)
        cardEngineParams = findViewById(R.id.cardEngineParams)
        cardExecution = findViewById(R.id.cardExecution)
    }

    private fun animateCardsOnLaunch() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.fade_in_up)
        cardConnection.startAnimation(anim)
        cardTextConsole.startAnimation(anim)
        cardEngineParams.startAnimation(anim)
        cardExecution.startAnimation(anim)
    }

    private fun setupUI() {
        btnProBadge.setOnClickListener { showPaywallDialog() }
        btnCodeVault.setOnClickListener { showCodeVaultDialog() }

        textInputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.length ?: 0
                charCountTextView.text = "$count chars"
            }
        })

        btnPasteClipboard.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.getItemAt(0)
            val pasteText = item?.text?.toString()
            if (!pasteText.isNullOrEmpty()) {
                textInputEditText.setText(pasteText)
                Toast.makeText(this, "📋 Pasted ${pasteText.length} characters from Clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnSanitizeCode.setOnClickListener {
            val raw = textInputEditText.text.toString()
            if (raw.isNotEmpty()) {
                val cleaned = sanitizeCodeForIde(raw)
                textInputEditText.setText(cleaned)
                Toast.makeText(this, "✨ Formatted! Brackets & Indents adjusted for IDE", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Console is empty", Toast.LENGTH_SHORT).show()
            }
        }

        btnClearText.setOnClickListener { textInputEditText.setText("") }
        btnSampleText1.setOnClickListener {
            textInputEditText.setText("fun main() {\n    println(\"Hello Cyberpunk HID World!\")\n}")
        }
        btnSampleText2.setOnClickListener {
            textInputEditText.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
        }
        btnSampleText3.setOnClickListener {
            textInputEditText.setText("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG 1234567890 !@#$%^&*()")
        }

        // 1-Click Profile Preset Click Handlers
        profileGhostChip.setOnClickListener {
            speedSlider.value = 75f
            randomDelayCheckBox.isChecked = true
            jitterSlider.isEnabled = true
            jitterSlider.values = listOf(40f, 120f)
            humanizeCheckBox.isChecked = true
            fluctuationSlider.isEnabled = true
            fluctuationSlider.value = 25f
            typoSlider.value = 2f
            thinkingPausesCheckBox.isChecked = true
            typingService?.isInstantMode = false
            Toast.makeText(this, "🕵️ Ghost Stealth Profile Loaded (100% Undetectable)", Toast.LENGTH_SHORT).show()
        }

        profileStudentChip.setOnClickListener {
            speedSlider.value = 55f
            randomDelayCheckBox.isChecked = true
            jitterSlider.isEnabled = true
            jitterSlider.values = listOf(50f, 160f)
            humanizeCheckBox.isChecked = true
            fluctuationSlider.isEnabled = true
            fluctuationSlider.value = 30f
            typoSlider.value = 3.5f
            thinkingPausesCheckBox.isChecked = true
            typingService?.isInstantMode = false
            Toast.makeText(this, "🧑‍💻 Natural Student Profile Loaded (55 WPM Casual)", Toast.LENGTH_SHORT).show()
        }

        profileExpertChip.setOnClickListener {
            speedSlider.value = 110f
            randomDelayCheckBox.isChecked = true
            jitterSlider.isEnabled = true
            jitterSlider.values = listOf(20f, 60f)
            humanizeCheckBox.isChecked = true
            fluctuationSlider.isEnabled = true
            fluctuationSlider.value = 15f
            typoSlider.value = 1f
            thinkingPausesCheckBox.isChecked = true
            typingService?.isInstantMode = false
            Toast.makeText(this, "⚡ Fast Touch-Typist Profile Loaded (110 WPM)", Toast.LENGTH_SHORT).show()
        }

        chip60Wpm.setOnClickListener { 
            typingService?.isInstantMode = false
            speedSlider.value = 60f 
        }
        chip150Wpm.setOnClickListener { 
            typingService?.isInstantMode = false
            speedSlider.value = 150f 
        }
        chip300Wpm.setOnClickListener { 
            typingService?.isInstantMode = false
            speedSlider.value = 300f 
        }
        chip600Wpm.setOnClickListener { 
            typingService?.isInstantMode = false
            speedSlider.value = 600f 
        }
        chip1200Wpm.setOnClickListener { 
            typingService?.isInstantMode = false
            speedSlider.value = 1200f 
        }
        chip3000Wpm.setOnClickListener { 
            typingService?.isInstantMode = false
            speedSlider.value = 3000f 
        }
        chipInstant.setOnClickListener { 
            typingService?.isInstantMode = true
            speedSlider.value = 5000f
            speedLabel.text = "Typing Speed: ⚡ INSTANT ULTRA (0ms Delay)"
        }

        speedSlider.addOnChangeListener { _, value, _ ->
            val valInt = value.toInt()
            if (valInt >= 5000) {
                typingService?.isInstantMode = true
                speedLabel.text = "Typing Speed: ⚡ INSTANT ULTRA (0ms Delay)"
            } else {
                typingService?.isInstantMode = false
                val badge = if (valInt >= 2000) "🚀 HYPER TURBO " else if (valInt >= 1000) "⚡ TURBO " else ""
                speedLabel.text = "Typing Speed: ${badge}${valInt} WPM (Max: 5000 WPM)"
            }
            typingService?.baseWpm = value.toDouble()
        }
        
        jitterSlider.addOnChangeListener { slider, _, _ ->
            val values = slider.values
            val min = values[0].toLong()
            val max = values[1].toLong()
            jitterLabel.text = "Random Delay Range (Jitter): ${min}ms - ${max}ms"
            typingService?.minJitter = min
            typingService?.maxJitter = max
        }
        
        humanizeCheckBox.setOnCheckedChangeListener { _, isChecked ->
            typingService?.isHumanizeEnabled = isChecked
            fluctuationSlider.isEnabled = isChecked
        }

        thinkingPausesCheckBox.setOnCheckedChangeListener { _, isChecked ->
            typingService?.isThinkingPausesEnabled = isChecked
        }

        fluctuationSlider.addOnChangeListener { _, value, _ ->
            fluctuationLabel.text = "Speed Fluctuation Range: ±${value.toInt()} WPM"
            typingService?.fluctuationRange = value.toDouble()
        }

        typoSlider.addOnChangeListener { _, value, _ ->
            typoLabel.text = "Typo Frequency: ${value.toInt()}%"
            typingService?.typoProbability = value.toDouble() / 100.0
        }
        
        randomDelayCheckBox.setOnCheckedChangeListener { _, isChecked ->
            typingService?.isRandomDelayEnabled = isChecked
            jitterSlider.isEnabled = isChecked
        }

        connectButton.setOnClickListener { 
            selectedBluetoothDevice?.let { 
                Toast.makeText(this, "Connecting to device...", Toast.LENGTH_SHORT).show()
                typingService?.bluetoothHidManager?.connect(it) 
            } 
        }
        
        disconnectButton.setOnClickListener { typingService?.bluetoothHidManager?.disconnect() }
        
        btnMakeDiscoverable.setOnClickListener { makePhoneDiscoverable() }
        btnAudioFix.setOnClickListener { showAudioFixDialog() }
        btnChangeIdentity.setOnClickListener { showIdentityDisguiseDialog() }
        
        startTypingButton.setOnClickListener { 
            if (isCountingDown) {
                cancelCountdown()
                return@setOnClickListener
            }

            syncServiceParams()
            val text = textInputEditText.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "Please enter payload text first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            typingService?.currentText = text

            if (countdownDelayCheckBox.isChecked) {
                startCountdownAndExecute()
            } else {
                executeStartTyping()
            }
        }
        
        stopTypingButton.setOnClickListener { 
            cancelCountdown()
            typingService?.stopTyping()
            updateTypingUI(false)
            updateButtonStates()
            Toast.makeText(this, "Auto-typing stopped", Toast.LENGTH_SHORT).show()
        }
    }

    data class KeyboardProfile(val name: String, val provider: String, val description: String)

    private fun applySavedIdentity() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentAdapterName = typingService?.bluetoothHidManager?.getAdapterName() ?: ""
        if (!prefs.contains("original_phone_name") && currentAdapterName.isNotEmpty() && !currentAdapterName.contains("Logitech") && !currentAdapterName.contains("Keyboard")) {
            prefs.edit().putString("original_phone_name", currentAdapterName).apply()
        }
        val savedName = prefs.getString("spoofed_name", "Logitech MX Keys S") ?: "Logitech MX Keys S"
        val savedProvider = prefs.getString("spoofed_provider", "Logitech Inc.") ?: "Logitech Inc."
        val savedDesc = prefs.getString("spoofed_desc", "Logitech Wireless Bluetooth Keyboard") ?: "Logitech Wireless Bluetooth Keyboard"
        
        tvActiveIdentity.text = savedName
        typingService?.bluetoothHidManager?.applyKeyboardDisguise(savedName, savedProvider, savedDesc)
    }

    private fun showIdentityDisguiseDialog() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val originalName = prefs.getString("original_phone_name", "My Android Phone") ?: "My Android Phone"
        
        val profiles = listOf(
            KeyboardProfile("Logitech MX Keys S", "Logitech Inc.", "Logitech Wireless Bluetooth Keyboard"),
            KeyboardProfile("Apple Magic Keyboard", "Apple Inc.", "Apple Magic Wireless Keyboard"),
            KeyboardProfile("Logitech K380", "Logitech Inc.", "Logitech K380 Multi-Device Bluetooth Keyboard"),
            KeyboardProfile("Dell KB700 Keyboard", "Dell Technologies", "Dell Premier Multi-Device Wireless Keyboard"),
            KeyboardProfile("HP 350 Compact Keyboard", "HP Inc.", "HP 350 Multi-Device Bluetooth Keyboard")
        )
        
        val options = mutableListOf<String>()
        profiles.forEach { options.add("⌨️ ${it.name} (${it.provider})") }
        options.add("✏️ Custom Keyboard Name...")
        options.add("🔄 Restore Original Phone Name ($originalName)")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🎭 Bluetooth Identity Disguise")
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which < profiles.size -> {
                        val selected = profiles[which]
                        setAndSaveIdentity(selected.name, selected.provider, selected.description)
                    }
                    which == profiles.size -> {
                        showCustomIdentityDialog()
                    }
                    else -> {
                        setAndSaveIdentity(originalName, "Android", "Android Device")
                        Toast.makeText(this, "Original name restored: $originalName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomIdentityDialog() {
        val input = EditText(this).apply {
            hint = "e.g. Corsair K70 Wireless"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom Keyboard Name")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val customName = input.text.toString().trim()
                if (customName.isNotEmpty()) {
                    setAndSaveIdentity(customName, "HID Peripheral Inc.", "$customName Bluetooth Keyboard")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setAndSaveIdentity(name: String, provider: String, description: String) {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("spoofed_name", name)
            .putString("spoofed_provider", provider)
            .putString("spoofed_desc", description)
            .apply()

        tvActiveIdentity.text = name
        typingService?.bluetoothHidManager?.applyKeyboardDisguise(name, provider, description)
        Toast.makeText(this, "🎭 Disguised! Broadcasted as: $name", Toast.LENGTH_LONG).show()
    }

    private fun makePhoneDiscoverable() {
        if (!checkBluetoothEnabled()) return
        typingService?.bluetoothHidManager?.let { hid ->
            if (!hid.isAppRegistered) {
                hid.registerApp()
            }
        }
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180)
        }
        startActivity(discoverableIntent)
        Toast.makeText(
            this,
            "📡 Phone is now discoverable as Keyboard! On Laptop: Open Bluetooth -> 'Add device' to pair.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showAudioFixDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🛠️ Laptop Audio Block / Keyboard Fix")
            .setMessage(
                "Why Laptop Blocks or Sees Phone as Audio:\n" +
                "Android phones by default enable Media Audio & Call profiles when pairing. If paired before registering the app, Windows caches the phone as an audio headset instead of a keyboard.\n\n" +
                "HOW TO FIX (3 Easy Steps):\n\n" +
                "1. ON YOUR PHONE:\n" +
                "   • Tap 'Open Bluetooth Settings' below.\n" +
                "   • Tap the ⚙️ Gear icon next to your Laptop's name.\n" +
                "   • Turn OFF 'Media Audio' and 'Calls'.\n" +
                "   • Turn ON 'Input Device' (HID).\n\n" +
                "2. IF ALREADY PAIRED AS AUDIO DEVICE:\n" +
                "   • On Laptop: Settings > Bluetooth > 'Remove Device'.\n" +
                "   • On Phone: Bluetooth > 'Unpair' Laptop.\n" +
                "   • Tap '📡 Pair as Keyboard' above, then re-pair from Laptop.\n\n" +
                "3. ON WINDOWS (OPTIONAL):\n" +
                "   • Press Win+R, run 'control printers'.\n" +
                "   • Right-click Phone > Properties > Services tab.\n" +
                "   • Uncheck 'Audio Sink' and 'Handsfree', keep 'Keyboard/HID' checked."
            )
            .setPositiveButton("Open Phone Bluetooth") { _, _ ->
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Got It", null)
            .show()
    }

    private fun startCountdownAndExecute() {
        isCountingDown = true
        startTypingButton.setBackgroundColor(ContextCompat.getColor(this, R.color.warning))
        
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                startTypingButton.text = "⏳ STARTING IN ${seconds}s... (TAP TO CANCEL)"
                try {
                    val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    v?.vibrate(50)
                } catch (e: Exception) {}
            }

            override fun onFinish() {
                isCountingDown = false
                startTypingButton.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.secondary))
                startTypingButton.text = getString(R.string.start_typing)
                executeStartTyping()
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        isCountingDown = false
        startTypingButton.setBackgroundColor(ContextCompat.getColor(this, R.color.secondary))
        startTypingButton.text = getString(R.string.start_typing)
        Toast.makeText(this, "Countdown Cancelled", Toast.LENGTH_SHORT).show()
    }

    private fun executeStartTyping() {
        if (typingService?.startTyping() == true) {
            updateTypingUI(true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (startTypingButton.isEnabled && !(typingService?.isTyping?.get() ?: false)) {
                    startTypingButton.performClick()
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (isCountingDown) {
                    cancelCountdown()
                    return true
                } else if (typingService?.isTyping?.get() == true) {
                    stopTypingButton.performClick()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showCodeVaultDialog() {
        val snippets = snippetVaultManager.getAllSnippets()
        val titles = snippets.map { "[${it.language}] ${it.title}" }.toMutableList()
        titles.add(0, "➕ 💾 Save Current Console to Vault")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🗄️ Offline Code Vault")
            .setItems(titles.toTypedArray()) { _, which ->
                if (which == 0) {
                    showSaveSnippetDialog()
                } else {
                    val selected = snippets[which - 1]
                    textInputEditText.setText(selected.code)
                    Toast.makeText(this, "Loaded: ${selected.title}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSaveSnippetDialog() {
        val currentCode = textInputEditText.text.toString()
        if (currentCode.isEmpty()) {
            Toast.makeText(this, "Console is empty, nothing to save!", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Snippet Title (e.g. DFS Graph Algorithm)"
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Save New Snippet")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val title = input.text.toString().ifEmpty { "Saved Snippet" }
                snippetVaultManager.saveSnippet(
                    CodeSnippet(
                        id = System.currentTimeMillis().toString(),
                        title = title,
                        language = "Custom",
                        code = currentCode,
                        category = "User Vault"
                    )
                )
                Toast.makeText(this, "💾 Saved to Code Vault!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPaywallDialog() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isPro = prefs.getBoolean("is_pro_user", false)

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (isPro) "👑 HyperType Studio PRO (Active)" else "⭐ Upgrade to HyperType PRO")
            .setMessage(
                "👑 100% Biometric Cognitive Engine (Anti-Detection)\n" +
                "🤫 Zero-Touch Silent Volume Triggers\n" +
                "🗄️ Unlimited Offline Code Vault\n" +
                "🧹 Smart IDE Bracket & Indent Sanitizer\n" +
                "⚡ 5000 WPM Instant Stream\n\n" +
                "Global Tier 1 Pricing: $4.99 USD / ₹299 (Lifetime Access)"
            )
        
        if (!isPro) {
            builder.setPositiveButton("Unlock Lifetime ($4.99)") { _, _ ->
                prefs.edit().putBoolean("is_pro_user", true).apply()
                btnProBadge.text = "👑 PRO"
                btnProBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_lime))
                Toast.makeText(this, "🎉 Lifetime PRO Membership Activated!", Toast.LENGTH_LONG).show()
            }
            builder.setNegativeButton("Maybe Later", null)
        } else {
            builder.setPositiveButton("OK", null)
        }
        builder.show()
    }

    private fun savePersistentSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("speed_wpm", speedSlider.value)
            .putBoolean("humanize", humanizeCheckBox.isChecked)
            .putBoolean("thinking_pauses", thinkingPausesCheckBox.isChecked)
            .putBoolean("smart_ide", smartIdeModeCheckBox.isChecked)
            .putFloat("fluctuation", fluctuationSlider.value)
            .putFloat("typo_prob", typoSlider.value)
            .putBoolean("auto_clear", autoClearCheckBox.isChecked)
            .apply()
    }

    private fun loadPersistentSettings() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_pro_user", false)) {
            btnProBadge.text = "👑 PRO"
            btnProBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_lime))
        }
        if (prefs.contains("speed_wpm")) {
            speedSlider.value = prefs.getFloat("speed_wpm", 75f)
            humanizeCheckBox.isChecked = prefs.getBoolean("humanize", true)
            thinkingPausesCheckBox.isChecked = prefs.getBoolean("thinking_pauses", true)
            smartIdeModeCheckBox.isChecked = prefs.getBoolean("smart_ide", true)
            fluctuationSlider.value = prefs.getFloat("fluctuation", 25f)
            typoSlider.value = prefs.getFloat("typo_prob", 2f)
            autoClearCheckBox.isChecked = prefs.getBoolean("auto_clear", true)
        }
    }

    private fun syncServiceParams() {
        typingService?.let {
            it.baseWpm = speedSlider.value.toDouble()
            it.isInstantMode = speedSlider.value >= 5000f
            it.isHumanizeEnabled = humanizeCheckBox.isChecked
            it.isThinkingPausesEnabled = thinkingPausesCheckBox.isChecked
            it.isSmartIdeMode = smartIdeModeCheckBox.isChecked
            it.fluctuationRange = fluctuationSlider.value.toDouble()
            it.typoProbability = typoSlider.value.toDouble() / 100.0
            it.isRandomDelayEnabled = randomDelayCheckBox.isChecked
            val jitterValues = jitterSlider.values
            it.minJitter = jitterValues[0].toLong()
            it.maxJitter = jitterValues[1].toLong()
        }
        savePersistentSettings()
    }

    private fun sanitizeCodeForIde(raw: String): String {
        var code = raw.trim()
        // 1. Normalize line endings
        code = code.replace("\r\n", "\n")
        
        // 2. Remove redundant trailing closing braces if pasted inside an open block
        val openBraces = code.count { it == '{' }
        val closeBraces = code.count { it == '}' }
        if (closeBraces > openBraces) {
            var diff = closeBraces - openBraces
            val sb = StringBuilder(code)
            for (i in sb.length - 1 downTo 0) {
                if (diff <= 0) break
                if (sb[i] == '}') {
                    sb.deleteCharAt(i)
                    diff--
                }
            }
            code = sb.toString().trimEnd()
        }
        
        // 3. Trim trailing line whitespaces
        val lines = code.split("\n")
        val processedLines = lines.map { it.trimEnd() }
        return processedLines.joinToString("\n")
    }

    private fun updateTypingUI(isTyping: Boolean) {
        startTypingButton.isEnabled = !isTyping
        stopTypingButton.visibility = if (isTyping) View.VISIBLE else View.GONE
        if (isTyping) {
            statusDot.setBackgroundResource(R.drawable.bg_status_dot_typing)
            statusDot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
            liveSpeedBadge.text = "⚡ TRANSMITTING"
        } else {
            statusDot.clearAnimation()
            updateButtonStates()
        }
    }

    override fun onProgressUpdate(stats: TypingService.LiveTypingStats) {
        runOnUiThread {
            liveWpmValueTextView.text = stats.liveWpm.toInt().toString()
            charsPerSecTextView.text = "%.1f chars/s".format(stats.charsPerSec)
            avgWpmTextView.text = "Avg: ${stats.avgWpm.toInt()} WPM"
            elapsedTimeTextView.text = "⏱️ %.1fs".format(stats.elapsedMs / 1000.0)
            
            typingProgressTextView.text = "Transmitted ${stats.currentChars} / ${stats.totalChars} chars (${stats.percentage}%)"
            typingProgressBar.progress = stats.percentage

            val badgeText = when {
                typingService?.isInstantMode == true -> "⚡ INSTANT 0ms"
                stats.liveWpm >= 2000 -> "🚀 HYPER TURBO"
                stats.liveWpm >= 800 -> "⚡ TURBO"
                stats.liveWpm >= 250 -> "🔥 FAST"
                else -> "⚡ ACTIVE"
            }
            liveSpeedBadge.text = badgeText
            updateTypingUI(true)
        }
    }

    override fun onTypingFinished(totalChars: Int, totalTimeMs: Long, avgWpm: Double) {
        runOnUiThread {
            updateTypingUI(false)
            val timeSec = totalTimeMs / 1000.0
            liveSpeedBadge.text = "✓ DONE"
            typingProgressTextView.text = "✓ Finished: $totalChars chars in %.2fs (Avg: %d WPM)".format(timeSec, avgWpm.toInt())

            // 1. Auto-Clear Console when finished
            if (autoClearCheckBox.isChecked) {
                textInputEditText.setText("")
            }

            // 2. Bring App to Foreground / Fullscreen on Finish
            try {
                val bringToFrontIntent = Intent(this@MainActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(bringToFrontIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Bring to front error", e)
            }

            // 3. Success Vibration Alert
            try {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(200)
            } catch (e: Exception) {}

            Toast.makeText(this, "Transmission Complete! $totalChars chars at ${avgWpm.toInt()} WPM", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val toRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            if (checkBluetoothEnabled()) updatePairedDevices()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            val bluetoothGranted = permissions.indices
                .filter { permissions[it] != Manifest.permission.POST_NOTIFICATIONS }
                .all { grantResults[it] == PackageManager.PERMISSION_GRANTED }
            if (bluetoothGranted && checkBluetoothEnabled()) {
                typingService?.bluetoothHidManager?.startHidDeviceService()
                updatePairedDevices()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkBluetoothEnabled(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!adapter.isEnabled) {
            requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return false
        }
        typingService?.bluetoothHidManager?.startHidDeviceService()
        return true
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDevices() {
        val devices = typingService?.bluetoothHidManager?.getPairedDevices() ?: emptyList()
        pairedDevices = devices
        val names = pairedDevices.map { it.name ?: it.address }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        deviceAutoCompleteTextView.setAdapter(adapter)
        deviceAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            selectedBluetoothDevice = pairedDevices[position]
            updateButtonStates()
        }
        if (pairedDevices.isNotEmpty() && selectedBluetoothDevice == null) {
            selectedBluetoothDevice = pairedDevices[0]
            deviceAutoCompleteTextView.setText(names[0], false)
            updateButtonStates()
        }
    }

    private fun updateButtonStates() {
        val device = selectedBluetoothDevice
        val manager = typingService?.bluetoothHidManager
        @SuppressLint("MissingPermission")
        val isConnected = device != null && manager?.bluetoothHidDevice?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        
        runOnUiThread {
            connectButton.isEnabled = selectedBluetoothDevice != null && manager?.isAppRegistered == true && !isConnected
            disconnectButton.isEnabled = isConnected
            startTypingButton.isEnabled = isConnected && !(typingService?.isTyping?.get() ?: false)

            if (isConnected) {
                statusDot.setBackgroundResource(R.drawable.bg_status_dot_connected)
                statusDot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
                connectionStatusTextView.text = "Status: Connected to ${device?.name ?: device?.address}"
                connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                statusDot.clearAnimation()
                statusDot.setBackgroundResource(R.drawable.bg_status_dot_disconnected)
                connectionStatusTextView.text = "Status: Disconnected"
                connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
        }
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        runOnUiThread {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (device != null) {
                        selectedBluetoothDevice = device
                        deviceAutoCompleteTextView.setText(device.name ?: device.address, false)
                    }
                    connectionStatusTextView.text = "Status: Connected to ${device?.name ?: device?.address}"
                    connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.primary))
                    statusDot.setBackgroundResource(R.drawable.bg_status_dot_connected)
                    statusDot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    connectionStatusTextView.text = "Status: Connecting..."
                    connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.secondary))
                    statusDot.setBackgroundResource(R.drawable.bg_status_dot_typing)
                }
                else -> {
                    connectionStatusTextView.text = "Status: Disconnected"
                    connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.error))
                    statusDot.clearAnimation()
                    statusDot.setBackgroundResource(R.drawable.bg_status_dot_disconnected)
                }
            }
            updateButtonStates()
        }
    }

    override fun onConnectionError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            updateButtonStates()
        }
    }

    override fun onAppRegistered(registered: Boolean) {
        runOnUiThread {
            if (registered) {
                connectionStatusTextView.text = "Status: HID ready — select a device and connect"
                connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.primary))
            } else {
                connectionStatusTextView.text = "Status: Bluetooth HID unavailable"
                connectionStatusTextView.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
            updateButtonStates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
