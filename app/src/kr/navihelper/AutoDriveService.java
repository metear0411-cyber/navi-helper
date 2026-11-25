package kr.navihelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;

/**
 * 티맵 홈 화면(검색창 옆 안전운전 버튼이 보이는 상태)에서
 * 사용자가 N초 동안 티맵을 조작하지 않으면 그 버튼을 누른다.
 * 런처 안 가상 화면(임베디드 창)에 떠 있는 티맵도 찾는다.
 */
public class AutoDriveService extends AccessibilityService {
    static final String TAG = "TmapAuto";
    static final String PKG = "com.skt.tmap.ku";
    static final String BTN = PKG + ":id/button_safety_driving";
    static final long CHECK_MS = 1000;
    /** 티맵 업데이트로 버튼 id가 바뀌어도 찾을 수 있게: 버튼 설명 글자(옛 버전 contentDescription "안전운행"). */
    static final String[] BTN_WORDS = {"안전운행", "안전운전", "안전 운행", "안전 운전"};
    /** id 일부로 찾기(버튼 이름이 조금 바뀐 경우). */
    static final String[] BTN_ID_PARTS = {"button_safety_driv", "btn_safety_driv"};
    /** 이게 보이면 이미 주행 화면이다 → 아무것도 누르지 않는다. */
    static final String[] DRIVING_IDS = {PKG + ":id/end_of_safe_drive_button", PKG + ":id/keep_driving_button"};

    final Handler h = new Handler(Looper.getMainLooper());
    long lastUserAction = System.currentTimeMillis();
    long visibleSince = 0;
    long lastClick = 0;
    long selfClickAt = 0;   // 이 앱이 누른 클릭은 사람 조작으로 세지 않기 위해
    long lastAdClose = 0;
    int adCloseBurst = 0;      // 1분 안에 연달아 '닫은' 횟수
    long adBurstFrom = 0;
    long adPauseUntil = 0;     // 닫아도 안 사라지는 글자를 계속 누르지 않도록 쉬는 시각

    /** 티맵 광고 팝업의 '오늘은 그만보기' 류 버튼 글자. */
    static final String[] AD_SKIP = {"오늘은 그만보기", "오늘 그만보기", "오늘 하루 보지 않기", "오늘 하루 그만보기", "다시 보지 않기",
            "그만보기", "그만 보기", "보지 않기", "보지않기"};

    final Runnable tick = new Runnable() {
        @Override public void run() {
            try { check(); } catch (Throwable t) { Log.w(TAG, "check", t); }
            h.postDelayed(this, CHECK_MS);
        }
    };

    EmbedDensity density;
    static AutoDriveService instance;

    @Override protected void onServiceConnected() {
        Log.i(TAG, "connected");
        Prefs.note(this, "자동 주행 서비스 시작");
        instance = this;
        h.removeCallbacks(tick);
        h.postDelayed(tick, CHECK_MS);
        if (density == null) density = new EmbedDensity(this);
        density.start();
    }

    /** 설정 화면에서 값을 바꿨을 때 호출. */
    static void densitySettingsChanged(boolean on) {
        AutoDriveService s = instance;
        if (s == null || s.density == null) return;
        if (on) s.density.applyAll(); else s.density.resetAll();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || !PKG.contentEquals(String.valueOf(e.getPackageName()))) return;
        switch (e.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                // 광고/배너 자동 슬라이드는 사람 조작이 아니므로 무시
                if (isAutoScroll(e)) break;
                lastUserAction = System.currentTimeMillis();
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                if (System.currentTimeMillis() - selfClickAt < 1500) break; // 이 앱이 누른 것
                lastUserAction = System.currentTimeMillis();
                break;
            case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
            case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
                lastUserAction = System.currentTimeMillis();
                break;
            default:
                break;
        }
    }

    static boolean isAutoScroll(AccessibilityEvent e) {
        String cls = String.valueOf(e.getClassName()).toLowerCase();
        if (cls.contains("viewpager")) return true;
        AccessibilityNodeInfo src = e.getSource();
        if (src == null) return true; // 출처를 모르면 사람 조작으로 보지 않음
        String id = String.valueOf(src.getViewIdResourceName()).toLowerCase();
        return id.contains("pager") || id.contains("banner") || id.contains("_ad") || id.contains("ad_")
                || id.contains("video") || id.contains("shortform");
    }

    // 이번 점검에서 본 티맵 상태(원인 찾기 기록용)
    boolean sawTmap, sawDriving;
    String frontPkg = "";

    AccessibilityNodeInfo findButton() {
        sawTmap = false; sawDriving = false; frontPkg = "";
        SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
        for (int i = 0; i < all.size(); i++) {
            for (AccessibilityWindowInfo w : all.valueAt(i)) {
                // 기본: 전체 화면(본 화면, display 0)의 티맵만. 홈 분할 칸(가상 화면)은 건너뜀.
                if (w.getDisplayId() != android.view.Display.DEFAULT_DISPLAY && !Prefs.inEmbedded(this)) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root != null && w.getDisplayId() == android.view.Display.DEFAULT_DISPLAY
                        && w.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && frontPkg.isEmpty())
                    frontPkg = String.valueOf(root.getPackageName());
                if (root == null || !PKG.contentEquals(String.valueOf(root.getPackageName()))) continue;
                sawTmap = true;
                AccessibilityNodeInfo n = findIn(root);
                if (n != null) { btnDisplay = w.getDisplayId(); return n; }
            }
        }
        return null;
    }

    int btnDisplay = android.view.Display.DEFAULT_DISPLAY;
    String lastFoundBy = "";

    /** 1) 원래 id  2) 설명 글자 "안전운행"  3) id 일부 — 순서로 찾는다. */
    AccessibilityNodeInfo findIn(AccessibilityNodeInfo root) {
        for (String id : DRIVING_IDS) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByViewId(id)) {
                if (n.isVisibleToUser()) { sawDriving = true; return null; } // 이미 주행 중
            }
        }
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByViewId(BTN)) {
            if (n.isVisibleToUser() && n.isEnabled()) { noteFound("id"); return n; }
        }
        for (String word : BTN_WORDS) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(word)) {
                if (!n.isVisibleToUser()) continue;
                CharSequence t = n.getContentDescription() != null ? n.getContentDescription() : n.getText();
                // "안전운전 종료"처럼 다른 말이 붙은 건 건너뜀 — 글자가 정확히 같을 때만
                if (t == null || !word.contentEquals(t.toString().trim())) continue;
                noteFound("글자 '" + t + "'");
                return n;
            }
        }
        AccessibilityNodeInfo n = findByIdPart(root, 0);
        if (n != null) noteFound("id 일부 " + n.getViewIdResourceName());
        return n;
    }

    AccessibilityNodeInfo findByIdPart(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 40) return null;
        String id = n.getViewIdResourceName();
        if (id != null && n.isVisibleToUser()) {
            String low = id.toLowerCase();
            boolean bad = low.contains("end") || low.contains("exit") || low.contains("stop") || low.contains("finish");
            if (!bad) for (String part : BTN_ID_PARTS) if (low.contains(part)) return n;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findByIdPart(n.getChild(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    void noteFound(String by) {
        if (!by.equals(lastFoundBy)) { Log.i(TAG, "safety button found by " + by); lastFoundBy = by; }
    }

    /** 버튼(또는 버튼 크기 정도의 눌리는 부모)을 누른다. 실패하면 한가운데를 톡. */
    boolean pressButton(AccessibilityNodeInfo btn) {
        Rect br = new Rect();
        btn.getBoundsInScreen(br);
        AccessibilityNodeInfo n = btn;
        for (int depth = 0; n != null && depth < 3; depth++, n = n.getParent()) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            // 검색창 같은 큰 묶음을 누르지 않도록 버튼의 2배 넘는 부모는 건너뜀
            if (depth > 0 && (r.width() > br.width() * 2 || r.height() > br.height() * 2)) break;
            if ((n.isClickable() || depth == 0) && n.isEnabled()) {
                selfClickAt = System.currentTimeMillis();
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        return tapCenter(br);
    }

    boolean tapCenter(Rect r) {
        if (r == null || r.isEmpty()) return false;
        Path p = new Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        GestureDescription.Builder b = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
        b.setDisplayId(btnDisplay);
        selfClickAt = System.currentTimeMillis();
        boolean ok = dispatchGesture(b.build(), null, null);
        Log.i(TAG, "tap gesture at " + r.centerX() + "," + r.centerY() + " display " + btnDisplay + ": " + ok);
        return ok;
    }


    /** 클릭 가능한 자신 또는 가장 가까운 부모를 누른다. */
    boolean clickNodeOrParent(AccessibilityNodeInfo n) {
        for (int depth = 0; n != null && depth < 4; depth++, n = n.getParent()) {
            if (n.isClickable() && n.isEnabled()) {
                selfClickAt = System.currentTimeMillis();
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    /** 티맵 광고 팝업이 떠 있으면 '오늘은 그만보기'를 눌러 닫는다. 닫았으면 true. */
    boolean closeAdPopup() {
        if (!Prefs.closeAd(this)) return false;
        long now = System.currentTimeMillis();
        if (now - lastAdClose < 2000 || now < adPauseUntil) return false;
        SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
        for (int i = 0; i < all.size(); i++) {
            for (AccessibilityWindowInfo w : all.valueAt(i)) {
                if (w.getDisplayId() != android.view.Display.DEFAULT_DISPLAY && !Prefs.inEmbedded(this)) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || !PKG.contentEquals(String.valueOf(root.getPackageName()))) continue;
                for (String word : AD_SKIP) {
                    for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(word)) {
                        if (!n.isVisibleToUser()) continue;
                        CharSequence t = n.getText() != null ? n.getText() : n.getContentDescription();
                        if (t == null || t.length() > 20) continue; // 긴 안내문 속 낱말은 건너뜀
                        if (clickNodeOrParent(n)) {
                            markAdClosed(now, "'" + t + "'");
                            return true;
                        }
                        // '그만보기' 글자가 눌리지 않는 팝업이면, 같은 창의 짧은 '닫기'를 누른다
                        for (AccessibilityNodeInfo x : root.findAccessibilityNodeInfosByText("닫기")) {
                            CharSequence xt = x.getText() != null ? x.getText() : x.getContentDescription();
                            if (!x.isVisibleToUser() || xt == null || xt.length() > 4) continue;
                            if (clickNodeOrParent(x)) {
                                markAdClosed(now, "'닫기' ('" + t + "' 옆)");
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    void markAdClosed(long now, String what) {
        lastAdClose = now;
        if (now - adBurstFrom > 60000) { adBurstFrom = now; adCloseBurst = 0; }
        adCloseBurst++;
        Log.i(TAG, "closed ad popup via " + what);
        if (adCloseBurst >= 3) {
            // 1분에 세 번 넘게 '닫았는데' 또 있다 = 광고가 아니라 늘 떠 있는 글자. 10분 쉰다.
            adPauseUntil = now + 10 * 60000;
            adCloseBurst = 0;
            Prefs.note(this, "광고 닫기: " + what + " 가 안 사라져 10분 쉼");
        } else {
            Prefs.note(this, "광고 닫음: " + what);
        }
    }

    void check() {
        // 광고를 닫은 틱에는 누르지만 않고, 기다린 시간은 그대로 둔다(예전엔 0으로 돌려서
        // 늘 떠 있는 글자를 '광고'로 잘못 보면 자동 주행이 영영 안 눌렸다).
        if (closeAdPopup()) return;
        if (!Prefs.enabled(this)) { visibleSince = 0; return; }
        long now = System.currentTimeMillis();
        AccessibilityNodeInfo btn = findButton();
        noteState(now, btn != null);
        if (btn == null) { visibleSince = 0; return; }
        if (visibleSince == 0) visibleSince = now;
        long waitMs = Prefs.delaySec(this) * 1000L;
        long idleFrom = Math.max(visibleSince, lastUserAction);
        if (now - idleFrom >= waitMs && now - lastClick > waitMs) {
            selfClickAt = now;
            boolean ok = pressButton(btn);
            lastClick = now;
            visibleSince = 0;
            Log.i(TAG, "clicked safety driving button: " + ok);
            Prefs.note(this, "자동 주행 누름(" + lastFoundBy + "): " + (ok ? "성공" : "실패"));
        }
    }

    /** 티맵 상태가 바뀔 때만 한 줄 남긴다: 주행 화면 / 첫 화면 / 화면에 없음. */
    String state = "";
    long stateSince = 0;
    String pending = "";
    long pendingSince = 0;
    void noteState(long now, boolean homeBtn) {
        String st = sawDriving ? "주행" : homeBtn ? "첫화면" : sawTmap ? "기타" : "없음";
        if (st.equals(state)) { pending = ""; return; }
        // 화면 전환 중 잠깐 스친 상태는 기록하지 않음: 3초 넘게 이어져야 인정
        if (!st.equals(pending)) { pending = st; pendingSince = now; return; }
        if (now - pendingSince < 3000) return;
        pending = "";
        now = pendingSince;
        String prev = state;
        long dur = stateSince == 0 ? 0 : (now - stateSince) / 1000;
        state = st; stateSince = now;
        if (prev.isEmpty()) return;
        String took = " (직전 " + prev + " " + (dur / 60) + "분 " + (dur % 60) + "초)";
        String msg;
        switch (st) {
            case "주행": msg = "티맵 주행 화면 들어감"; break;
            case "첫화면":
                msg = "주행".equals(prev) ? "티맵 주행이 풀림 → 첫 화면" : "티맵 첫 화면";
                break;
            case "기타": msg = "티맵 다른 화면(검색·메뉴 등)"; break;
            default: msg = "티맵이 화면에서 사라짐, 앞 화면: " + (frontPkg.isEmpty() ? "알 수 없음" : frontPkg); break;
        }
        if ("주행".equals(prev) && now - lastUserAction < 5000) msg += ", 5초 안에 사람 조작 있었음";
        Log.i(TAG, msg + took);
        Prefs.note(this, msg + took);
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        h.removeCallbacks(tick);
        if (density != null) density.stop();
        instance = null;
        super.onDestroy();
    }
}
