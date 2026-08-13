# Custom Reminders

A barebones Flutter/Android app that fires hourly reminder notifications, 5 minutes before the hour, from 10:55 AM to 10:55 PM every day. Built for personal use — not published to any app store.

## Features
- Exact daily alarms (10:55, 11:55, ..., 22:55), scheduled and handled entirely by native Android (`AlarmManager` + `BroadcastReceiver`s), so reminders fire even if the app isn't open.
- Notifications play the device's default alarm sound (or vibration-only, if toggled off) and include **Snooze (5 min)** and **Dismiss** actions.
- Reminders are automatically skipped while the phone is in Do Not Disturb mode.
- Reminders survive device reboots.
- Minimal UI with two switches: pause/resume reminders, and toggle sound vs. vibration-only.

## Requirements
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable channel)
- Android SDK command-line tools (`platform-tools`, a recent `platforms;android-XX`, and matching `build-tools`)
- A JDK (17+)
- An Android phone with USB debugging enabled, or an emulator

Run `flutter doctor` to confirm your toolchain is set up correctly before building.

## Build and install on a phone
1. Clone this repo and `cd` into it:
   ```sh
   git clone https://github.com/acarl005/custom-reminders.git
   cd custom-reminders
   ```
2. Fetch dependencies:
   ```sh
   flutter pub get
   ```
3. Connect your Android phone via USB with **USB debugging** enabled (Settings → About phone → tap "Build number" 7 times → Developer options → enable USB debugging), and accept the "Allow USB debugging?" prompt on the phone.
4. Confirm the device is detected:
   ```sh
   flutter devices
   ```
5. Build and install directly onto the connected device:
   ```sh
   flutter install
   ```
   (or `flutter run --release` to build, install, and stream logs in one step)

## After installing
Open the app once and grant the two permission prompts it shows if needed:
- **Exact alarm permission** (required on Android 12+ for reminders to fire on time)
- **Notification permission** (required on Android 13+ for reminders to show at all)

## Project layout
- `lib/main.dart` — the Flutter UI (pause/sound toggles, permission banners)
- `android/app/src/main/kotlin/dev/andy/custom_reminders/` — all native alarm scheduling, notification building, and Do Not Disturb logic
- `assets/icon/` — source SVGs for the launcher icon
