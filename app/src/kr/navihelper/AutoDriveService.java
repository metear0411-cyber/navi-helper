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
    static final String[] BTN_ID_PARTS = {"safety_driving", "safe_driving", "safety_drive", "safe_drive_button", "btn_safe_drive"};

    final Handler h = new Handler(Looper.getMainLooper());
    long lastUserAction = System.currentTimeMillis();
    long visibleSince = 0;
    long lastClick = 0;
    long selfClickAt = 0;   // 이 앱이 누른 클릭은 사람 조작으로 세지 않기 위해
    long lastAdClose = 0;

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

    AccessibilityNodeInfo findButton() {
        SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
        for (int i = 0; i < all.size(); i++) {
            for (AccessibilityWindowInfo w : all.valueAt(i)) {
                // 기본: 전체 화면(본 화면, display 0)의 티맵만. 홈 분할 칸(가상 화면)은 건너뜀.
                if (w.getDisplayId() != android.view.Display.DEFAULT_DISPLAY && !Prefs.inEmbedded(this)) continue;
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || !PKG.contentEquals(String.valueOf(root.getPackageName()))) continue;
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
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByViewId(BTN)) {
            if (n.isVisibleToUser() && n.isEnabled()) { noteFound("id"); return n; }
        }
        for (String word : BTN_WORDS) {
            for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(word)) {
                if (!n.isVisibleToUser()) continue;
                CharSequence t = n.getContentDescription() != null ? n.getContentDescription() : n.getText();
                if (t == null || t.length() > 8) continue; // 안내문·메뉴 글 속 낱말은 건너뜀
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
            for (String part : BTN_ID_PARTS) if (low.contains(part)) return n;
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

    long verifyAt = 0;  // 누른 뒤 화면이 바뀌었는지 볼 시각

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
        if (now - lastAdClose < 2000) return false;
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
                            lastAdClose = now;
                            Log.i(TAG, "closed ad popup via '" + t + "'");
                            return true;
                        }
                        // '그만보기' 글자가 눌리지 않는 팝업이면, 같은 창의 짧은 '닫기'를 누른다
                        for (AccessibilityNodeInfo x : root.findAccessibilityNodeInfosByText("닫기")) {
                            CharSequence xt = x.getText() != null ? x.getText() : x.getContentDescription();
                            if (!x.isVisibleToUser() || xt == null || xt.length() > 4) continue;
                            if (clickNodeOrParent(x)) {
                                lastAdClose = now;
                                Log.i(TAG, "closed ad popup via '닫기' (next to '" + t + "')");
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    void check() {
        if (closeAdPopup()) { visibleSince = 0; return; }
        if (!Prefs.enabled(this)) { visibleSince = 0; verifyAt = 0; return; }
        long now = System.currentTimeMillis();
        AccessibilityNodeInfo btn = findButton();
        if (verifyAt != 0 && now >= verifyAt) {
            verifyAt = 0;
            // 누른다고 했는데 2초 뒤에도 버튼이 그대로면(클릭이 안 먹음) 손가락 톡으로 한 번 더
            if (btn != null && now - lastUserAction > 2000) {
                Rect r = new Rect();
                btn.getBoundsInScreen(r);
                Log.i(TAG, "button still visible after click, retry with tap");
                tapCenter(r);
                visibleSince = 0;
                return;
            }
        }
        if (btn == null) { visibleSince = 0; return; }
        if (visibleSince == 0) visibleSince = now;
        long waitMs = Prefs.delaySec(this) * 1000L;
        long idleFrom = Math.max(visibleSince, lastUserAction);
        if (now - idleFrom >= waitMs && now - lastClick > waitMs) {
            selfClickAt = now;
            boolean ok = pressButton(btn);
            lastClick = now;
            visibleSince = 0;
            verifyAt = now + 2000;
            Log.i(TAG, "clicked safety driving button: " + ok);
        }
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        h.removeCallbacks(tick);
        if (density != null) density.stop();
        instance = null;
        super.onDestroy();
    }
}
