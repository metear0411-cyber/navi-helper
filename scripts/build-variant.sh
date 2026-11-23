#!/bin/bash
# 예전 개인용 빌드(kr.local.tmapauto)를 쓰던 기기에 덮어 설치할 판을 만든다.
# 같은 소스에서 패키지 이름만 kr.navihelper → kr.local.tmapauto 로 바꾸고,
# versionCode는 일반판 + 1 로 한다(1.2.0 때 6/7 규칙 그대로).
# 서명은 그 기기에 깔린 옛 키(CN=local)로: KEYSTORE / KEYSTORE_PASS 환경 변수.
# 결과: app/out/navi-helper-kr.local.tmapauto.apk
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NEW=kr.local.tmapauto
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp -r "$ROOT/app" "$ROOT/build.sh" "$TMP/"
[ -d "$ROOT/libs" ] && cp -r "$ROOT/libs" "$TMP/"
rm -rf "$TMP/app/out"
mkdir -p "$TMP/app/src/kr/local"
mv "$TMP/app/src/kr/navihelper" "$TMP/app/src/kr/local/tmapauto"
grep -rl "kr\.navihelper" "$TMP/app" | xargs sed -i "s/kr\.navihelper/$NEW/g"
VC=$(grep -o 'versionCode="[0-9]*"' "$TMP/app/AndroidManifest.xml" | grep -o '[0-9]*')
sed -i "s/versionCode=\"$VC\"/versionCode=\"$((VC + 1))\"/" "$TMP/app/AndroidManifest.xml"
bash "$TMP/build.sh"
mkdir -p "$ROOT/app/out"
cp "$TMP/app/out/navi-helper.apk" "$ROOT/app/out/navi-helper-$NEW.apk"
echo "완료: app/out/navi-helper-$NEW.apk (versionCode $((VC + 1)))"
