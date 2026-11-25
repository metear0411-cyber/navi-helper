# 개발·릴리스 안내 (관리자용)

이 문서만 읽으면 **어느 PC, 어느 작업 세션에서든** 저장소를 받아 이어서 작업하고 릴리스할 수 있게 적어 둔다.
서명 키는 GitHub 시크릿에 있어서, **릴리스에는 로컬 키가 필요 없다.** 태그만 올리면 된다.

## 1. 한눈에
| 항목 | 값 |
|---|---|
| 저장소 | `metear0411-cyber/navi-helper` (공개, 기본 브랜치 `main`) |
| 일반판 | 패키지 `kr.navihelper`, 산출물 `navi-helper.apk` |
| 개인판 | 패키지 `kr.local.tmapauto`, 산출물 `navi-helper-kr.local.tmapauto.apk` — 관리자 차 네비 1대 전용(아래 4절) |
| 빌드 방식 | Gradle 없음. `build.sh`가 aapt2 → javac → d8 → zipalign → apksigner |
| 필요 도구 | JDK 17, Android SDK `build-tools;34.0.0`, `platforms;android-33` |
| 외부 라이브러리 | `libs/` Shizuku API 13.1.5 (MIT, NOTICE 참고) |
| 대상 기기 | FYT UIS7870 / 안드로이드 13 / 런처 `com.syu.launcher10001` |

## 2. 릴리스하는 법 (평소엔 이것만)
1. `app/AndroidManifest.xml`의 `versionCode`를 **1 올리고** `versionName`도 바꾼다.
   - 개인판은 빌드 때 자동으로 `versionCode + 1`이 된다(`scripts/build-variant.sh`).
2. 커밋 메시지 본문에 바뀐 점을 적는다. **태그가 가리키는 커밋의 메시지가 그대로 릴리스 노트**가 된다.
3. `main`에 push.
4. 태그를 올린다.
   ```
   git tag v1.2.3
   git push origin v1.2.3
   ```
5. Actions의 `release` 워크플로가 두 APK를 빌드·서명해서 Releases에 붙인다.
   ```
   gh run watch -R metear0411-cyber/navi-helper
   ```
6. 받은 APK의 서명 지문을 확인한다(3절 표와 같아야 함).
   ```
   gh release download v1.2.3 -R metear0411-cyber/navi-helper -D ./dl
   apksigner verify --print-certs ./dl/navi-helper.apk
   ```

손으로 시험 빌드만 하려면 Actions에서 `release` 워크플로를 **Run workflow**(workflow_dispatch)로 돌린다. 태그가 아니면 Releases에는 올리지 않고 아티팩트로만 남는다.

## 3. 서명 키
| 판 | Owner | SHA-256 지문 | GitHub 시크릿 |
|---|---|---|---|
| 일반판 | `CN=navi-helper` (별칭 `release`) | `C1:E3:ED:0D:A7:0A:82:3F:AB:E7:03:DB:84:C4:71:20:44:39:EA:39:0E:14:07:86:48:EF:3F:FB:54:78:29:A9` | `NAVI_KEYSTORE_B64`, `NAVI_KEYSTORE_PASS` |
| 개인판 | `CN=local` (별칭 `k`) | `AD:60:B8:85:A5:48:12:60:0B:40:E9:44:AA:48:5D:F0:25:FA:31:06:A2:5A:AE:C5:E9:E5:10:4C:0F:61:8C:B6` | `NAVI_LOCAL_KEYSTORE_B64`, `NAVI_LOCAL_KEYSTORE_PASS` |

- `*_B64`는 키 파일(PKCS12)을 base64로 바꾼 것, `*_PASS`는 그 비밀번호.
- **키 파일과 비밀번호의 원본 백업**은 관리자 NAS의 개인 홈 폴더 `navi-helper-서명키`에 있다(`읽어주세요.txt` 포함). 저장소·이슈·채팅 어디에도 올리지 않는다.
- 일반판 키는 2026-11-23에 새로 만들었다. 그 전 키(`D1:7E:34:37…`)는 분실했고, 그래서 1.2.1 이하 일반판 사용자는 1.2.2로 갈 때 한 번 지우고 다시 설치해야 한다.
- 키를 잃어버리면 기존 사용자가 덮어 설치를 못 한다. 시크릿을 지우거나 바꾸기 전에 NAS 백업이 있는지 먼저 확인한다.
- `build.sh`의 `KEYSTORE_PASS` 기본값(`android`)은 **로컬 시험용**이다. 실제 키 비밀번호가 아니다.

## 4. 개인판(kr.local.tmapauto)이 따로 있는 이유
- 관리자 차 네비에 처음 깐 앱이 개인 빌드(`kr.local.tmapauto`, `CN=local` 키)였다.
- 그 앱에는 PC로 받아 둔 `WRITE_SECURE_SETTINGS` 권한이 있다. **앱을 지우면 권한이 사라지고, 다시 받으려면 PC나 Shizuku가 필요**하다.
- 그래서 같은 소스를 패키지만 바꿔 같은 키로 서명한 판을 계속 함께 낸다. 일반 사용자는 받지 않는다.
- 관리자 네비 업데이트: 네비 브라우저로 Releases에서 `navi-helper-kr.local.tmapauto.apk`를 받아 **덮어 설치**(지우지 않기).

## 5. 로컬에서 빌드하기
```
export ANDROID_SDK=/path/to/android-sdk
export KEYSTORE=/path/to/your.keystore     # 없으면 시험용 키를 새로 만든다
export KEYSTORE_PASS=...                   # 실제 키면 그 비밀번호
./build.sh                                 # → app/out/navi-helper.apk
KEYSTORE=... KEYSTORE_PASS=... bash scripts/build-variant.sh   # → 개인판
```
Windows에서는 Git Bash로 돌린다(`build.sh`가 `.exe`/`.bat` 도구를 알아서 고른다).
로컬에서 만든 APK를 사용자에게 배포하지 않는다. 배포는 항상 Actions 릴리스로 한다(키를 로컬에 둘 필요가 없게).

## 6. 코드 구조 (`app/src/kr/navihelper/`)
| 파일 | 하는 일 |
|---|---|
| `AutoDriveService.java` | 접근성 서비스 본체. 티맵 자동 주행 버튼 누르기, 광고 팝업 닫기, 사람 조작 감지(광고 배너 자동 스크롤은 무시) |
| `EmbedDensity.java` | 런처 홈 분할 칸 가상 화면(`Embedded_Task@…`)의 dpi를 `IWindowManager.setForcedDisplayDensityForUser(id, dpi, 0)`로 맞춤 |
| `AccEnabler.java` | `WRITE_SECURE_SETTINGS`로 `enabled_accessibility_services`에 이 앱을 다시 넣음 |
| `BootReceiver.java`, `RecheckJob.java` | 부팅 직후·10초·30초·90초·3분 뒤 접근성 확인 |
| `KeepAliveWallpaper.java` | 쓰지 않는 빈 배경화면 서비스. FYT가 시동 끌 때 이 앱을 강제 종료하지 않게 하는 용도(아래 7절) |
| `ShizukuGrant.java` | Shizuku 셸 권한으로 `pm grant <이 앱> WRITE_SECURE_SETTINGS` 1회 실행 |
| `HelpText.java` | 앱 안 도움말(README 설치 내용과 맞춰 둘 것) |
| `MainActivity.java` | 설정 화면 |
| `Prefs.java` | 설정 저장 키 |

## 7. 기기·앱에서 알아낸 사실 (다시 조사하지 않아도 되게)
**티맵 (`com.skt.tmap.ku`, 11.8.x 기준)**
- 홈 검색창 옆 화살표(주행 시작) = `com.skt.tmap.ku:id/button_safety_driving`
- 홈 상단 검색 줄 = `tmap_main_search_bar_layout`, 마이크 = `adot_home_wake_up_button`
- 홈에 자동으로 넘어가는 광고 배너(`inside_ad_image_pager`, ViewPager2)가 있어서, 스크롤 이벤트를 전부 사람 조작으로 치면 대기 시간이 계속 초기화된다.
- 광고 팝업은 '오늘은 그만보기' 류 글자, 안 눌리면 같은 창의 짧은 '닫기'.

**FYT UIS7870 / 안드로이드 13**
- 런처 `launcher10001`의 홈 분할 칸은 가상 디스플레이. 이름 `Embedded_Task@<해시>`, dpi = 본 화면 240 × 고정 비율 = 160. 런처가 칸을 다시 만들 때마다 id가 바뀐다.
- 가상 디스플레이 회전 고정은 `SET_ORIENTATION` 권한이 필요해서 앱으로는 못 한다(본 화면은 adb `cmd window fixed-to-user-rotation enabled`로 가능).
- 재부팅·시동 때 FYT가 접근성 설정을 초기화하는 경우가 있다 → `AccEnabler`.
- **시동 끔 = 재부팅이 아니라 잠들기(休眠)**. 그래서 `BOOT_COMPLETED`가 오지 않는다. 잠들 때 `com.syu.ms`(`a.k.D()`→`B()`)가 실행 중인 앱을 거의 다 `forceStopPackage`한다. 안드로이드는 강제 종료된 앱의 접근성 서비스를 `enabled_accessibility_services`에서 지우고, 강제 종료된 앱은 앱을 직접 열기 전까지 어떤 방송도 못 받는다 → 시동 켜도 자동 주행이 죽은 채(11-25 원장 기기 기록으로 확인: 09:25 시동 끔 뒤 16:32 앱을 열어서야 다시 켜짐).
- 강제 종료 예외: `com.syu.ms` 안 `assets/app_category/protected_app.txt`(정규식 find, 고칠 수 없음), 홈 런처 앱, 키보드(`InputMethod`) 앱, **배경화면 서비스(`WallpaperService` 인텐트)를 선언한 앱**. 마지막 것을 이용해 1.2.5부터 빈 배경화면 서비스를 둔다(meta-data가 없어서 배경화면 고르기 목록엔 안 나옴).
- `setForcedDisplayDensityForUser`의 사용자 인자로 `-2`(USER_CURRENT)를 주면 `INTERACT_ACROSS_USERS_FULL`이 필요해서 실패한다. `0`을 쓴다.
- 와이파이 STA+AP 동시 지원 안 함(핫스팟 켜면 와이파이 클라이언트 꺼짐).

## 8. 지킬 것
- 키 파일, base64, 비밀번호를 커밋·이슈·로그·채팅에 남기지 않는다. 시크릿은 파일에서 `gh secret set`으로 바로 넘긴다.
- `main`에 강제 push 하지 않는다.
- 공개판에 넣는 기능은 티맵 약관 위반 소지를 한 번 생각한다(광고 닫기는 설정에서 끌 수 있게 둠).
- 사용자 기기 정보(펌웨어 덤프, 설정값, 개인 정보)는 저장소에 올리지 않는다.

## 9. 다음 할 일 후보
- 1.2.5 실기기 확인: 시동 껐다 켠 뒤 최근 동작에 '자동 주행 서비스 시작'·'접근성이 꺼져 있어서 다시 켬'이 **안** 찍히고 티맵이 자동으로 주행되면 해결
- 티맵 버튼을 ID 말고 글자·설명으로도 찾기(티맵 업데이트 대비)
- 앱에 진단 정보 복사 버튼(제보 받을 때 쓰기)
- Shizuku 흐름 실기기 검증(관리자 네비는 이미 권한이 있어 미검증)
- 다른 기기 제보 정리(UIS7865: 자동 주행 동작 확인)
