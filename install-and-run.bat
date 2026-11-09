@echo off
set "ADB=C:\Users\ainol\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "DESKTOP_APK=C:\Users\ainol\OneDrive\Desktop\FeederMY-v1.0.4-lucide.apk"

echo [1/4] Copying APK to Desktop...
copy /Y "%APK%" "%DESKTOP_APK%"

echo [2/4] Installing updated APK to emulator-5554...
"%ADB%" -s emulator-5554 install -r "%APK%"

echo [3/4] Launching MainActivity on emulator-5554...
"%ADB%" -s emulator-5554 shell am start -n my.feeder.app/.MainActivity

echo [4/4] Done!
