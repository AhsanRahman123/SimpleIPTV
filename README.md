# Simple IPTV (Android TV)

Tiny Android TV player. Ships with 2 Zee Bangla links; add/remove your own in the app.

## Get the APK without installing anything
1. Create a new GitHub repo, upload this whole folder (keep `.github/`).
2. Open the repo's Actions tab -> "Build APK" -> run it (it also runs on every push).
3. When it finishes, download the `SimpleIPTV-debug-apk` artifact, unzip it, you get `app-debug.apk`.

## Or with Android Studio
Open this folder, let it sync, Build > Build APK(s).

## Install on the TV
Copy app-debug.apk to a USB drive / use "Send Files to TV" / `adb install app-debug.apk`,
then open it with a file manager on the TV (allow "install unknown apps" if asked).

## Use
- OK on a channel = play. Hold OK on a channel = delete.
- "+ Add channel": name + stream URL.
- "+ Import m3u playlist": paste a playlist URL, all channels in it are added.
