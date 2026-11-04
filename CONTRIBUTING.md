# Contributing to Phone Bluetooth AutoTyper

Thank you for contributing to the Phone Bluetooth AutoTyper project! ⌨️📱

---

## Development Setup

1. **Android Studio**: Android Studio Ladybug or newer with Android SDK API 34+ installed.
2. **Device Requirements**: Physical Android device with Bluetooth 5.0+ and `BluetoothHidDevice` API support.
3. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```
4. **Install on device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Pull Request Guidelines
- Follow standard Kotlin style guidelines.
- Test pairing with at least Windows and macOS host machines.
- Ensure volume-button accessibility shortcuts continue to function reliably.
