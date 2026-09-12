package com.example.bluetoothkeyboard

import android.app.*
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class TypingService : Service(), BluetoothHidManager.ConnectionListener {

    private val TAG = "TypingService"
    private val CHANNEL_ID = "TypingServiceChannel"
    private val NOTIFICATION_ID = 1

    private val binder = LocalBinder()
    lateinit var bluetoothHidManager: BluetoothHidManager
    
    private var typingThread: Thread? = null
    val isTyping = AtomicBoolean(false)
    val maxOutputWpm = 5000.0

    @Volatile var currentText = ""
    @Volatile var baseWpm = 60.0
    @Volatile var isHumanizeEnabled = false
    @Volatile var isRandomDelayEnabled = false
    @Volatile var minJitter = 0L
    @Volatile var maxJitter = 100L
    @Volatile var typoProbability = 0.0
    @Volatile var fluctuationRange = 25.0
    @Volatile var isInstantMode = false
    @Volatile var isThinkingPausesEnabled = false
    @Volatile var isSmartIdeMode = false

    data class LiveTypingStats(
        val currentChars: Int,
        val totalChars: Int,
        val percentage: Int,
        val liveWpm: Double,
        val avgWpm: Double,
        val charsPerSec: Double,
        val elapsedMs: Long
    )

    interface ProgressListener {
        fun onProgressUpdate(stats: LiveTypingStats)
        fun onTypingFinished(totalChars: Int, totalTimeMs: Long, avgWpm: Double)
        fun onConnectionStateChanged(device: BluetoothDevice?, state: Int)
        fun onConnectionError(message: String)
        fun onAppRegistered(registered: Boolean)
    }
    
    private var progressListener: ProgressListener? = null

    inner class LocalBinder : Binder() {
        fun getService(): TypingService = this@TypingService
    }

    // QWERTY Physical Neighbors Table for 100% Realistic Typos
    private val qwertyNeighbors = mapOf(
        'a' to charArrayOf('q', 'w', 's', 'z'),
        'b' to charArrayOf('v', 'g', 'h', 'n', ' '),
        'c' to charArrayOf('x', 'd', 'f', 'v'),
        'd' to charArrayOf('s', 'e', 'r', 'f', 'c', 'x'),
        'e' to charArrayOf('w', 's', 'd', 'r', '3', '4'),
        'f' to charArrayOf('d', 'r', 't', 'g', 'v', 'c'),
        'g' to charArrayOf('f', 't', 'y', 'h', 'b', 'v'),
        'h' to charArrayOf('g', 'y', 'u', 'j', 'n', 'b'),
        'i' to charArrayOf('u', 'j', 'k', 'o', '8', '9'),
        'j' to charArrayOf('h', 'u', 'i', 'k', 'm', 'n'),
        'k' to charArrayOf('j', 'i', 'o', 'l', 'm', ','),
        'l' to charArrayOf('k', 'o', 'p', ';', '.'),
        'm' to charArrayOf('n', 'j', 'k', ','),
        'n' to charArrayOf('b', 'h', 'j', 'm'),
        'o' to charArrayOf('i', 'k', 'l', 'p', '9', '0'),
        'p' to charArrayOf('o', 'l', '[', '-'),
        'q' to charArrayOf('1', '2', 'w', 'a', 's'),
        'r' to charArrayOf('e', 'd', 'f', 't', '4', '5'),
        's' to charArrayOf('a', 'w', 'e', 'd', 'x', 'z'),
        't' to charArrayOf('r', 'f', 'g', 'y', '5', '6'),
        'u' to charArrayOf('y', 'h', 'j', 'i', '7', '8'),
        'v' to charArrayOf('c', 'f', 'g', 'b', ' '),
        'w' to charArrayOf('q', 'a', 's', 'e', '2', '3'),
        'x' to charArrayOf('z', 's', 'd', 'c'),
        'y' to charArrayOf('t', 'g', 'h', 'u', '6', '7'),
        'z' to charArrayOf('a', 's', 'x')
    )

    // Hand Affinity Sets (Left Hand vs Right Hand keys on standard QWERTY)
    private val leftHandChars = setOf(
        'q', 'w', 'e', 'r', 't', 'a', 's', 'd', 'f', 'g', 'z', 'x', 'c', 'v', 'b',
        'Q', 'W', 'E', 'R', 'T', 'A', 'S', 'D', 'F', 'G', 'Z', 'X', 'C', 'V', 'B',
        '1', '2', '3', '4', '5', '!', '@', '#', '$', '%'
    )
    private val rightHandChars = setOf(
        'y', 'u', 'i', 'o', 'p', 'h', 'j', 'k', 'l', 'n', 'm',
        'Y', 'U', 'I', 'O', 'P', 'H', 'J', 'K', 'L', 'N', 'M',
        '6', '7', '8', '9', '0', '^', '&', '*', '(', ')', '-', '=', '_', '+',
        '[', ']', '{', '}', ';', ':', '\'', '"', ',', '.', '<', '>', '/', '?', '\n'
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bluetoothHidManager = BluetoothHidManager(this, this)
        bluetoothHidManager.startHidDeviceService()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification("Ready to type"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Ready to type"))
        }
        return START_STICKY
    }

    fun setProgressListener(listener: ProgressListener?) {
        this.progressListener = listener
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bluetooth Keyboard Service")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "Typing Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    // Box-Muller Gaussian random generator for natural human biological distributions
    private fun sampleGaussian(random: Random, mean: Double, stdDev: Double): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-7)
        val u2 = random.nextDouble()
        val z0 = sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
        return mean + z0 * stdDev
    }

    // Log-Normal distribution generator for authentic Inter-Keystroke Intervals (IKI)
    private fun sampleLogNormal(random: Random, targetMedian: Double, sigma: Double): Double {
        val mu = ln(targetMedian)
        val normal = sampleGaussian(random, 0.0, 1.0)
        return exp(mu + sigma * normal)
    }

    fun startTyping(): Boolean {
        if (isTyping.get()) return false
        if (!bluetoothHidManager.isConnected()) {
            progressListener?.onConnectionError("Connect a Bluetooth host before starting")
            return false
        }
        isTyping.set(true)
        updateNotification("Typing...")
        
        typingThread = Thread {
            val startTimeNs = System.nanoTime()
            try {
                val random = Random(System.currentTimeMillis())
                val mainHandler = Handler(Looper.getMainLooper())
                val text = currentText
                var lastUiUpdateMs = 0L

                // Sliding window of recent timestamps for accurate real-time instantaneous WPM
                val windowSize = 20
                val windowTimestamps = LongArray(windowSize)
                var windowHead = 0
                var windowCount = 0

                var prevChar = ' '
                var charactersTypedSinceBreak = 0
                var fatigueWavePhase = random.nextDouble(0.0, Math.PI * 2)
                var buzzed25 = false
                var buzzed50 = false
                var buzzed75 = false

                var i = 0
                while (i < text.length) {
                    if (!isTyping.get()) break
                    val char = text[i]

                    // =========================================================================
                    // 1. BIOMETRIC LAG-CORRECTED REALISTIC TYPO ENGINE
                    // =========================================================================
                    if (!isInstantMode && i > 0 && typoProbability > 0.0 && random.nextDouble() < typoProbability && char.isLetter()) {
                        executeRealisticBiometricTypo(text, i, random)
                    }

                    if (!isTyping.get()) break

                    val hidKey = KeycodeMapper.getHidKey(char)

                    // =========================================================================
                    // 2. REALISTIC KEY DWELL TIME (HOLD DURATION) & SHIFT PRE-ENGAGEMENT
                    // =========================================================================
                    if (hidKey.keycode != 0 || hidKey.modifier != 0) {
                        // Shift pre-engagement latency (humans press Shift 30-70ms before letter)
                        if (hidKey.modifier != 0 && !isInstantMode && baseWpm < 400 && isHumanizeEnabled) {
                            bluetoothHidManager.sendKeyboardReport(hidKey.modifier, 0)
                            val shiftPreHold = random.nextLong(25, 65)
                            Thread.sleep(shiftPreHold)
                        }

                        // KEY DOWN
                        bluetoothHidManager.sendKeyboardReport(hidKey.modifier, hidKey.keycode)
                        
                        // Mechanical Key Dwell Time sampled from human biometric Gaussian distribution
                        val keyHoldDuration: Long = when {
                            isInstantMode -> 0L
                            baseWpm >= 800 -> 0L
                            baseWpm >= 300 -> 2L
                            isHumanizeEnabled -> {
                                val meanHold = when (char) {
                                    ' ', '\n' -> 68.0
                                    in 'A'..'Z' -> 58.0
                                    else -> 48.0
                                }
                                sampleGaussian(random, meanHold, 9.0).toLong().coerceIn(28L, 95L)
                            }
                            else -> 10L
                        }

                        if (keyHoldDuration > 0) {
                            Thread.sleep(keyHoldDuration)
                        }

                        // KEY UP
                        bluetoothHidManager.sendKeyboardReport(0, 0)
                    }

                    // =========================================================================
                    // 3. ADVANCED COGNITIVE DELAY & INTER-KEYSTROKE INTERVAL (IKI) ENGINE
                    // =========================================================================
                    if (!isInstantMode) {
                        // A. Base Target Milliseconds per Character
                        val targetMsPerChar = 60000.0 / (baseWpm.coerceIn(10.0, maxOutputWpm) * 5.0)
                        
                        // B. Biomechanical Fatigue & Flow Wave (Sinusoidal rhythm drift)
                        fatigueWavePhase += 0.08
                        val flowDriftFactor = if (isHumanizeEnabled) {
                            1.0 + (sin(fatigueWavePhase) * (fluctuationRange / 100.0))
                        } else 1.0

                        var activeCharDelayMs = targetMsPerChar * flowDriftFactor

                        // C. Log-Normal IKI Jitter (Replaces robotic flat uniform random)
                        if (isHumanizeEnabled) {
                            activeCharDelayMs = sampleLogNormal(random, activeCharDelayMs, 0.22)

                            // D. Fitts's Law & Digraph Hand Affinity Modifier
                            val isLeftCurr = leftHandChars.contains(char)
                            val isLeftPrev = leftHandChars.contains(prevChar)
                            val isRightCurr = rightHandChars.contains(char)
                            val isRightPrev = rightHandChars.contains(prevChar)

                            if ((isLeftCurr && isLeftPrev) || (isRightCurr && isRightPrev)) {
                                // Same-hand consecutive key press takes ~20% longer
                                activeCharDelayMs *= random.nextDouble(1.15, 1.35)
                            } else if ((isLeftCurr && isRightPrev) || (isRightCurr && isLeftPrev)) {
                                // Alternating hand finger-roll is ~15% faster
                                activeCharDelayMs *= random.nextDouble(0.82, 0.95)
                            }

                            // E. Token / Word Boundary Micro-Pauses
                            when (char) {
                                ' ' -> activeCharDelayMs += random.nextDouble(45.0, 120.0)
                                ',', '.', '!', '?', ':' -> activeCharDelayMs += random.nextDouble(120.0, 320.0)
                                '\n' -> activeCharDelayMs += random.nextDouble(150.0, 450.0)
                            }
                        }

                        // F. User-configured Jitter Range
                        if (isRandomDelayEnabled) {
                            val jitter = random.nextLong(minJitter, maxJitter + 1)
                            activeCharDelayMs += jitter
                        }

                        // Sleep the calculated IKI
                        val sleepTimeMs = activeCharDelayMs.toLong().coerceAtLeast(1L)
                        Thread.sleep(sleepTimeMs)

                        // =========================================================================
                        // 4. 5-TIER COGNITIVE THINKING & SYNTAX HESITATION ENGINE
                        // =========================================================================
                        charactersTypedSinceBreak++
                        if (isThinkingPausesEnabled) {
                            // Syntax boundaries: brackets, semi-colons, line-breaks, arrows
                            val isCodeSyntaxBoundary = (char == '\n' || char == ';' || char == '}' || char == '{' || char == ')' || char == ']')
                            
                            if (isCodeSyntaxBoundary) {
                                val roll = random.nextDouble()
                                when {
                                    // Tier 1: Deep logic reasoning pause (1.8s - 3.8s) - 20% chance
                                    roll < 0.20 -> {
                                        val pauseMs = random.nextLong(1800, 3800)
                                        Thread.sleep(pauseMs)
                                        charactersTypedSinceBreak = 0
                                    }
                                    // Tier 2: Quick syntax formatting review (400ms - 950ms) - 35% chance
                                    roll < 0.55 -> {
                                        val pauseMs = random.nextLong(400, 950)
                                        Thread.sleep(pauseMs)
                                    }
                                }
                            } else if (charactersTypedSinceBreak > random.nextInt(45, 95)) {
                                // Tier 3: Reading / problem statement consultation pause (2.0s - 4.5s)
                                val readPauseMs = random.nextLong(2000, 4500)
                                Thread.sleep(readPauseMs)
                                charactersTypedSinceBreak = 0
                            }
                        }
                    }

                    prevChar = char

                    // =========================================================================
                    // 5. LIVE TELEMETRY TELEGRAPHING
                    // =========================================================================
                    val nowNs = System.nanoTime()
                    val currentProgress = i + 1
                    val totalLength = text.length

                    windowTimestamps[windowHead] = nowNs
                    windowHead = (windowHead + 1) % windowSize
                    if (windowCount < windowSize) windowCount++

                    val nowMs = SystemClock.elapsedRealtime()
                    val uiInterval = if (isInstantMode) 50L else 33L
                    if (nowMs - lastUiUpdateMs >= uiInterval || i == totalLength - 1) {
                        lastUiUpdateMs = nowMs

                        val elapsedNs = (nowNs - startTimeNs).coerceAtLeast(1L)
                        val elapsedMs = elapsedNs / 1_000_000L
                        val elapsedSec = elapsedNs / 1_000_000_000.0

                        val overallAvgWpm = if (elapsedSec > 0.02) {
                            (currentProgress / 5.0) / (elapsedSec / 60.0)
                        } else baseWpm

                        val charsPerSec = if (elapsedSec > 0.02) {
                            currentProgress / elapsedSec
                        } else 0.0

                        val liveInstantWpm: Double
                        if (windowCount >= 5) {
                            val oldestIndex = (windowHead - windowCount + windowSize) % windowSize
                            val oldestNs = windowTimestamps[oldestIndex]
                            val deltaSec = (nowNs - oldestNs) / 1_000_000_000.0
                            liveInstantWpm = if (deltaSec > 0.001) {
                                (windowCount / 5.0) / (deltaSec / 60.0)
                            } else overallAvgWpm
                        } else {
                            liveInstantWpm = overallAvgWpm
                        }

                        val percentage = (currentProgress * 100) / totalLength
                        
                        // Silent Pocket Haptic Milestone Telemetry
                        if (percentage >= 25 && !buzzed25) {
                            buzzed25 = true
                            triggerHapticPulse(1)
                        } else if (percentage >= 50 && !buzzed50) {
                            buzzed50 = true
                            triggerHapticPulse(2)
                        } else if (percentage >= 75 && !buzzed75) {
                            buzzed75 = true
                            triggerHapticPulse(3)
                        }

                        val stats = LiveTypingStats(
                            currentChars = currentProgress,
                            totalChars = totalLength,
                            percentage = percentage,
                            liveWpm = liveInstantWpm,
                            avgWpm = overallAvgWpm,
                            charsPerSec = charsPerSec,
                            elapsedMs = elapsedMs
                        )

                        mainHandler.post {
                            progressListener?.onProgressUpdate(stats)
                        }
                    }

                    i++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Typing thread error", e)
            } finally {
                isTyping.set(false)
                typingThread = null
                val totalTimeNs = (System.nanoTime() - startTimeNs).coerceAtLeast(1L)
                val totalTimeMs = totalTimeNs / 1_000_000L
                val totalSec = totalTimeNs / 1_000_000_000.0
                val textLength = currentText.length
                val finalAvgWpm = if (totalSec > 0.01) (textLength / 5.0) / (totalSec / 60.0) else 0.0

                try {
                    bluetoothHidManager.sendKeyboardReport(0, 0)
                } catch (e: Exception) {}
                
                Handler(Looper.getMainLooper()).post {
                    progressListener?.onTypingFinished(textLength, totalTimeMs, finalAvgWpm)
                }
            }
        }.apply {
            priority = Thread.MAX_PRIORITY
            name = "HardwareSyncTypingThread"
            start()
        }
        return true
    }

    private fun triggerHapticPulse(count: Int) {
        try {
            val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = when (count) {
                    1 -> longArrayOf(0, 45)
                    2 -> longArrayOf(0, 40, 70, 40)
                    3 -> longArrayOf(0, 35, 60, 35, 60, 35)
                    else -> longArrayOf(0, 200)
                }
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, -1))
            } else {
                vibrator.vibrate(count * 45L)
            }
        } catch (e: Exception) {}
    }

    // Realistic human typo execution: hitting a QWERTY neighbor, 1-character overshoot, reaction pause, and rapid Backspaces
    private fun executeRealisticBiometricTypo(text: String, currentIndex: Int, random: Random) {
        if (!isTyping.get()) return
        val originalChar = text[currentIndex].lowercaseChar()
        val neighbors = qwertyNeighbors[originalChar] ?: charArrayOf('a', 's', 'd', 'f')
        val wrongChar = neighbors[random.nextInt(neighbors.size)]
        
        // 1. Type the adjacent wrong key
        val wrongHid = KeycodeMapper.getHidKey(wrongChar)
        bluetoothHidManager.sendKeyboardReport(wrongHid.modifier, wrongHid.keycode)
        Thread.sleep(random.nextLong(40, 75))
        bluetoothHidManager.sendKeyboardReport(0, 0)
        
        var charsToBackspace = 1

        // 2. 50% chance of 1-character overshoot (typing next char before brain catches mistake)
        if (currentIndex + 1 < text.length && random.nextBoolean()) {
            Thread.sleep(random.nextLong(60, 130))
            if (!isTyping.get()) return
            val overshootChar = text[currentIndex + 1]
            val overshootHid = KeycodeMapper.getHidKey(overshootChar)
            bluetoothHidManager.sendKeyboardReport(overshootHid.modifier, overshootHid.keycode)
            Thread.sleep(random.nextLong(38, 70))
            bluetoothHidManager.sendKeyboardReport(0, 0)
            charsToBackspace = 2
        }

        // 3. Human Visual/Cognitive Reaction Time ("Oops, typo!" delay)
        val reactionDelayMs = random.nextLong(180, 360)
        Thread.sleep(reactionDelayMs)

        // 4. Rapid rhythmic Backspacing to erase mistake
        val backspace = KeycodeMapper.getHidKey('\b')
        for (b in 0 until charsToBackspace) {
            if (!isTyping.get()) return
            bluetoothHidManager.sendKeyboardReport(backspace.modifier, backspace.keycode)
            Thread.sleep(random.nextLong(30, 55))
            bluetoothHidManager.sendKeyboardReport(0, 0)
            Thread.sleep(random.nextLong(80, 140))
        }

        // 5. Brief finger re-orientation pause before typing correct character
        Thread.sleep(random.nextLong(90, 220))
    }

    fun stopTyping() {
        isTyping.set(false)
        typingThread?.interrupt()
        typingThread = null
        try {
            bluetoothHidManager.sendKeyboardReport(0, 0)
        } catch (e: Exception) {}
        updateNotification("Ready to type")
    }

    fun emergencyStopTyping() {
        stopTyping()
        bluetoothHidManager.disconnect()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
        progressListener?.onConnectionStateChanged(device, state)
    }

    override fun onDevicesFound(devices: List<BluetoothDevice>) {}

    override fun onConnectionError(message: String) {
        Log.w(TAG, message)
        progressListener?.onConnectionError(message)
    }

    override fun onAppRegistered(registered: Boolean) {
        Log.d(TAG, "HID app registered: $registered")
        progressListener?.onAppRegistered(registered)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTyping()
        bluetoothHidManager.stopHidDeviceService()
    }
}
