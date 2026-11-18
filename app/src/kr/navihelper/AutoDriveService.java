package kr.navihelper;

import android.accessibilityservice.AccessibilityService;
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

    final Handler h = new Handler(Looper.getMainLooper());
    long lastUserAction = System.currentTimeMillis();
    long visibleSince = 0;
    long lastClick = 0;
    long selfClickAt = 0;   // 이 앱이 누른 클릭은 사람 조작으로 세지 않기 위해
    long lastAdClose = 0;

    /** 티맵 광고 팝업의 '오늘은 그만보기' 류 버튼 글자. */
    static final String[] AD_SKIP = {"오늘은 그만보기", "오늘 그만보기", "오늘 하루 보지 않기", "오늘 하루 그만보기", "다시 보지 않기", "그만보기"};

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
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(BTN);
                for (AccessibilityNodeInfo n : found) {
                    if (n.isVisibleToUser() && n.isEnabled()) return n;
                }
            }
        }
        return null;
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
                    }
                }
            }
        }
        return false;
    }

    void check() {
        if (closeAdPopup()) { visibleSince = 0; return; }
        if (!Prefs.enabled(this)) { visibleSince = 0; return; }
        long now = System.currentTimeMillis();
        AccessibilityNodeInfo btn = findButton();
        if (btn == null) { visibleSince = 0; return; }
        if (visibleSince == 0) visibleSince = now;
        long waitMs = Prefs.delaySec(this) * 1000L;
        long idleFrom = Math.max(visibleSince, lastUserAction);
        if (now - idleFrom >= waitMs && now - lastClick > waitMs) {
            selfClickAt = now;
            boolean ok = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            lastClick = now;
            visibleSince = 0;
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
