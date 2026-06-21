# Speed Reader 3D

Speed Reader 3D is now set up as a native Android Studio project.

It still has the earlier web files in the repo, but Android Studio can ignore those. The Android app lives in the normal `app/` module and runs as a regular Android app.

## Open it in Android Studio

1. Open Android Studio.
2. Choose **Open** and select the repo folder: `rork-speed-reader-3d`.
3. Let Android Studio finish **Gradle Sync**.
4. Press the green **Run** button.

If Android Studio asks to install a missing SDK or Gradle component, click **Install** or **Accept**. That is normal on a fresh machine.

## What the Android app does

- Paste or edit text inside the app.
- Tap **Play** to speed-read word by word.
- Adjust WPM from 100 to 900.
- Change chunk size from 1 to 4 words.
- Turn punctuation pauses on or off.
- Switch visual modes: Tunnel, Glass, or Plain.
- Save your text and reading settings automatically.

## Android project files

- `settings.gradle` tells Android Studio this is a Gradle Android project.
- `build.gradle` defines the Android Gradle Plugin.
- `app/build.gradle` defines the app module.
- `app/src/main/AndroidManifest.xml` defines the launchable Android app.
- `app/src/main/java/com/joemagic/speedreader3d/MainActivity.java` contains the native speed reader app.

## Optional web version

The older React/Vite version is still present. To use that version instead:

```bash
npm install
npm run dev
```

For now, Android Studio users should use the native Android project path above.
