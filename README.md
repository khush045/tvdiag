# TV NEXUS (v0.1.0) — UNCOMPILED, UNTESTED SOURCE

**Status: this project has never been built or run.** It was written in an environment with no Android SDK and
no access to Google's Maven. No APK exists. Nothing here has been tested on an emulator or a physical TV.
Expect to fix some compile errors on first build.

## What is implemented
| Module | State |
|---|---|
| Developer Options Doctor | Read-only diagnosis, snapshot-diff verification, report export, ADB command templates |
| Recovery Center | Boot info, capability-gated reboot (needs REBOOT permission), mode explanations, ADB templates |
| Plugin core | `ToolModule` interface + `ModuleRegistry` (one line to add a module) |

**Not implemented** (from the original spec): System Explorer, User Management Explorer, Storage Cleaner, App Manager,
Performance Lab, Network Lab, USB Lab, Log Viewer, Settings Explorer. They are deliberately absent from the UI
instead of shown as fake buttons.

## Build (on your computer)
1. Install Android Studio (Koala or newer) with Android SDK Platform 34. JDK 17+ (bundled).
2. File > Open > this folder. Let Gradle sync (needs internet for AGP 8.5.2 / Kotlin 1.9.24).
3. Build > Build APK(s), or `./gradlew assembleDebug` (generate the wrapper first: `gradle wrapper --gradle-version 8.7`).
4. Output: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed; rename to TV_NEXUS.apk if you like).
5. Install: from a computer `adb install app-debug.apk` (needs ADB), or copy to a USB stick and open it with a file
   manager app on the TV (needs "unknown sources" permission for that app).
6. Emulator test: create an "Android TV (1080p)" AVD, install, launch from the Leanback launcher.

If your TV's ADB is disabled (your case), use the USB-stick route.

## Permissions
- `QUERY_ALL_PACKAGES`: lets the Doctor see which Settings/Setup packages exist (package visibility, API 30+).
- `REBOOT`: declared only to *detect* whether it is granted. A sideloaded app will not get it, so reboot buttons stay disabled.
- No INTERNET permission. No storage permission. Reports go to the app's own external files folder.

## Compatibility (design intent, NOT test results)
minSdk 23, targetSdk 34, no third-party libraries. Hidden APIs (`isAdminUser`, `getUserCount`, `isRestrictedProfile`)
are called by reflection and shown as "null/unknown" if unavailable. Real-device compatibility: **untested**.
OnePlus/Skyworth firmware behaviour: **not verified**; AOSP and OEM Settings source were not inspected.

## Known limitations
- A normal APK cannot write `device_provisioned`, read Device Owner, list other users, or reboot. These need ADB or system privileges.
- ADB is currently off on the target TV and cannot be turned on without a working Developer Options screen; this
  app provides no workaround and will not bypass authentication.
- The "most likely cause" (device_provisioned=0 with user_setup_complete=1) is a hypothesis, not a confirmed cause.
- Placeholder banner/icon art.

## Build report
| Step | Result |
|---|---|
| Gradle compile | NOT RUN (no SDK / network blocked) |
| APK generated | NO |
| Install / launch test | NOT RUN |
| Static checks done | XML well-formedness, brace balance (see below) |
