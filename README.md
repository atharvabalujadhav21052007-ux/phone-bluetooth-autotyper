<div align="center">

# ⌨️ Phone Bluetooth AutoTyper

### Turn your Android phone into a stealth, driverless Bluetooth HID Hardware Keyboard & AutoTyper.

[![Android](https://img.shields.io/badge/Android-10.0%2B-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![Bluetooth HID](https://img.shields.io/badge/Protocol-Bluetooth%20HID-blue.svg)](https://www.bluetooth.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

<p align="center">
  <a href="#-how-it-works">How It Works</a> •
  <a href="#-key-features">Key Features</a> •
  <a href="#-human-jitter-simulation">Human Simulation</a> •
  <a href="#-pairing-guide">Pairing Guide</a> •
  <a href="#-building-the-apk">Build Guide</a> •
  <a href="#-license">License</a>
</p>

</div>

---

## ⚡ What is Phone Bluetooth AutoTyper?

**Phone Bluetooth AutoTyper** is an open-source Android application that registers your smartphone directly as a **Bluetooth HID (Human Interface Device) hardware keyboard**.

Because it operates at the native Bluetooth HID protocol level, **it requires ZERO client software, agents, or drivers on the target PC/Mac/Linux machine**. The host computer sees your phone as a standard physical wireless keyboard (just like a physical Logitech or Apple Magic Keyboard).

---

## ✨ Key Features

- 🎯 **100% Driverless & Zero Software on Target:** Works seamlessly with Windows, macOS, Linux, iPadOS, iOS, Android, and Smart TVs.
- 🚀 **Extreme Speed Tiers:**
  * `60 WPM` — Realistic human casual typing.
  * `150 WPM` — Fast professional typist.
  * `300 WPM` & `600 WPM` — Rapid code pasting.
  * `1200 WPM` & `3000 WPM` — Blazing high-speed injection.
  * `Instant Mode` — Raw packet bursts.
- 🧠 **Human Jitter & Anti-Detection Profiles:**
  * **Ghost Profile:** Chaotic Brownian random-walk delay intervals, deep thinking pauses, and natural keystroke dwell times.
  * **Student Profile:** Variable hesitation, occasional typos with automatic natural backspacing.
  * **Expert Profile:** High, steady throughput.
- 💻 **Smart IDE Mode:** Auto-sanitizes tabs, preserves indentation, and bypasses IDE automatic brace/quote matching to prevent syntax mangling.
- 🎛️ **Hardware Volume Button Triggers:**
  * **Volume Down:** Immediately initiates or resumes autotyping.
  * **Volume Up:** Emergency instant pause / abort.
- 📂 **Snippet Vault Manager:** Save, label, and manage recurring code snippets, shell commands, and templates with one-tap typing.

---

## 🔬 Human Jitter & Mathematical Indistinguishability

Standard automated typers produce flat, robotic inter-keystroke intervals (e.g. constant 20ms delays) which are trivially flagged by heuristic typing-cadence analyzers.

This engine utilizes a **continuous Brownian random-walk with aperiodic noise** to simulate real human muscle fatigue, cognitive pauses, and burst typing:

```
Human Jitter Timing Flow:
[Next Char] ──► [Base Inter-Key Delay] 
                     │
                     ▼
          [+ Brownian Drift (-15ms to +25ms)]
                     │
                     ▼
          [Thinking Pause? (Random comma/period dwell)]
                     │
                     ▼
          [Send HID Report (Down)] ──► [Dwell (50ms–110ms)] ──► [Send HID Release]
```

---

## 📲 Pairing & Setup Guide

1. **Enable Bluetooth** on your Android phone and host PC.
2. Open the **Phone Bluetooth AutoTyper** app on your phone.
3. Tap **"Register HID Service"** (grants standard Bluetooth HID device profile).
4. On your PC/Mac, open Bluetooth Settings and click **"Add Device"**.
5. Select your phone from the list and confirm pairing.
6. Once paired, your PC will display: *"Device connected as Keyboard"*.
7. Paste your code into the app, select your WPM profile, and click **"Start Typing"** (or press **Volume Down**)!

---

## 🛠️ Building the APK

### Prerequisites
* Android Studio Ladybug or newer
* Android SDK Platform API 34+
* JDK 17 or JDK 21

```bash
# Clone the repository
git clone https://github.com/atharvabalujadhav21052007-ux/phone-bluetooth-autotyper.git
cd phone-bluetooth-autotyper

# Build debug APK
./gradlew assembleDebug

# Install via ADB to connected phone
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 Repository Structure

```
phone-bluetooth-autotyper/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/bluetoothkeyboard/
│   │   │   ├── BluetoothHidManager.kt     # Native BluetoothHidDevice profile wrapper
│   │   │   ├── KeycodeMapper.kt           # Character-to-HID report translator
│   │   │   ├── MainActivity.kt            # Material 3 UI & event listeners
│   │   │   ├── SnippetVaultManager.kt     # Persistent snippet repository
│   │   │   └── TypingService.kt           # Foreground execution service & jitter engine
│   │   ├── res/                           # Layouts, themes, animations, drawables
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── CONTRIBUTING.md
├── LICENSE                                # MIT License
└── README.md
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
