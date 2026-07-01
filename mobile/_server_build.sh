#!/usr/bin/env bash
set -e
rm -rf /tmp/sc /work/android /work/build
flutter create --org br.com.salome --project-name torre_app --platforms=android /tmp/sc >/dev/null 2>&1
cp -r /tmp/sc/android /work/android
M=/work/android/app/src/main/AndroidManifest.xml
sed -i '/<manifest /a\    <uses-permission android:name="android.permission.INTERNET"/>\n    <uses-permission android:name="android.permission.CAMERA"/>\n    <uses-feature android:name="android.hardware.camera" android:required="false"/>' "$M"
sed -i '0,/<application/{s#<application#<application android:usesCleartextTraffic="true"#}' "$M"
echo "== manifest top =="; head -4 "$M"
# mobile_scanner 7.x exige minSdk >= 23 — força no build.gradle.kts gerado pelo flutter create.
G=/work/android/app/build.gradle.kts
sed -i 's/minSdk = flutter.minSdkVersion/minSdk = 23/' "$G"
echo "== minSdk =="; grep -n "minSdk" "$G"
flutter pub get >/dev/null 2>&1
echo "== building apk =="
flutter build apk --release --dart-define=TORRE_BASE_URL=http://187.127.32.124:8789 2>&1 | tail -8
cp /work/build/app/outputs/flutter-apk/app-release.apk /work/torre.apk
echo BUILD_DONE
