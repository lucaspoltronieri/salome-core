#!/usr/bin/env bash
set -e
rm -rf /tmp/sc /work/android /work/build
flutter create --org br.com.salome --project-name torre_app --platforms=android /tmp/sc >/dev/null 2>&1
cp -r /tmp/sc/android /work/android
M=/work/android/app/src/main/AndroidManifest.xml
sed -i '/<manifest /a\    <uses-permission android:name="android.permission.INTERNET"/>\n    <uses-permission android:name="android.permission.CAMERA"/>' "$M"
sed -i '0,/<application/{s#<application#<application android:usesCleartextTraffic="true"#}' "$M"
echo "== manifest top =="; head -4 "$M"
flutter pub get >/dev/null 2>&1
echo "== building apk =="
flutter build apk --release --dart-define=TORRE_BASE_URL=http://187.127.32.124:8789 2>&1 | tail -8
cp /work/build/app/outputs/flutter-apk/app-release.apk /work/torre.apk
echo BUILD_DONE
