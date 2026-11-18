#!/bin/bash
# Gradle 없이 Android SDK 명령줄 도구만으로 APK를 만든다.
# 필요: JDK 17, Android SDK (build-tools 34.0.0, platforms;android-33)
#
# 사용법:
#   export ANDROID_SDK=/path/to/android-sdk
#   export KEYSTORE=/path/to/your.keystore   # 없으면 새로 만든다 (저장소에 올리지 말 것)
#   ./build.sh
set -e
cd "$(dirname "$0")/app"

SDK="${ANDROID_SDK:?ANDROID_SDK 환경 변수를 SDK 경로로 지정하세요}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-33/android.jar"
KS="${KEYSTORE:-../release.keystore}"
KS_PASS="${KEYSTORE_PASS:-android}"

# Windows(Git Bash)에서는 .exe/.bat, 그 외에서는 확장자 없는 도구를 쓴다
if [ -f "$BT/aapt2.exe" ]; then
  AAPT2="$BT/aapt2.exe"; ZIPALIGN="$BT/zipalign.exe"
  D8() { cmd //c "$(cygpath -w "$BT/d8.bat")" "$@"; }
  APKSIGNER() { cmd //c "$(cygpath -w "$BT/apksigner.bat")" "$@"; }
  P() { cygpath -w "$1"; }
else
  AAPT2="$BT/aapt2"; ZIPALIGN="$BT/zipalign"
  D8() { "$BT/d8" "$@"; }
  APKSIGNER() { "$BT/apksigner" "$@"; }
  P() { echo "$1"; }
fi

rm -rf out
mkdir -p out/res out/classes out/dex
"$AAPT2" compile --dir res -o out/res/res.zip
"$AAPT2" link -I "$AJ" --manifest AndroidManifest.xml -o out/base.apk out/res/res.zip --java out/gen --auto-add-overlay
# libs/*.jar: Shizuku API (Apache-2.0, dev.rikka.shizuku 13.1.5)
SEP=":"; [ -f "$BT/aapt2.exe" ] && SEP=";"
CP="$(P "$AJ")"; LIBJARS=""
for j in ../libs/*.jar; do [ -f "$j" ] && CP="$CP$SEP$(P "$j")" && LIBJARS="$LIBJARS $(P "$j")"; done
javac -encoding UTF-8 -source 11 -target 11 -classpath "$CP" -d out/classes \
  $(find src out/gen -name '*.java')
D8 --min-api 30 --lib "$(P "$AJ")" --output "$(P out/dex)" $(find out/classes -name '*.class' | while read -r f; do P "$f"; done) $LIBJARS
cp out/base.apk out/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('out/unsigned.apk','a');z.write('out/dex/classes.dex','classes.dex');z.close()" 2>/dev/null \
  || python -c "import zipfile;z=zipfile.ZipFile('out/unsigned.apk','a');z.write('out/dex/classes.dex','classes.dex');z.close()"
"$ZIPALIGN" -f 4 out/unsigned.apk out/aligned.apk
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias release -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=navi-helper"
fi
APKSIGNER sign --ks "$(P "$KS")" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$(P out/navi-helper.apk)" "$(P out/aligned.apk)"
echo "완료: app/out/navi-helper.apk"
