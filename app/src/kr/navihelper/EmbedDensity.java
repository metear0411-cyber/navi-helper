package kr.navihelper;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import java.lang.reflect.Method;

/**
 * FYT 런처(launcher10001 등)가 홈 화면 분할 칸용으로 만드는 가상 화면(Embedded_Task@...)의
 * 밀도를 설정값으로 맞춘다. 런처가 창을 다시 만들 때마다 자동으로 다시 적용.
 * WRITE_SECURE_SETTINGS 권한 필요 (adb: pm grant kr.navihelper android.permission.WRITE_SECURE_SETTINGS).
 */
final class EmbedDensity implements DisplayManager.DisplayListener {
    static final String TAG = "TmapAuto";
    final Context ctx;
    final DisplayManager dm;
    final Handler h = new Handler(Looper.getMainLooper());

    EmbedDensity(Context c) {
        ctx = c.getApplicationContext();
        dm = ctx.getSystemService(DisplayManager.class);
    }

    void start() {
        dm.registerDisplayListener(this, h);
        applyAll();
    }

    void stop() { dm.unregisterDisplayListener(this); }

    static boolean isEmbedded(Display d) {
        return d != null && d.getDisplayId() != Display.DEFAULT_DISPLAY
                && String.valueOf(d.getName()).startsWith("Embedded_Task@");
    }

    void applyAll() {
        Display[] all = dm.getDisplays("android.hardware.display.category.ALL_INCLUDING_DISABLED");
        if (all == null || all.length <= 1) all = dm.getDisplays();
        for (Display d : all) {
            Log.i(TAG, "display " + d.getDisplayId() + " name=" + d.getName());
            apply(d);
        }
    }

    void apply(Display d) {
        if (!isEmbedded(d) || !Prefs.dpiOn(ctx)) return;
        int target = Prefs.dpi(ctx);
        DisplayMetrics m = new DisplayMetrics();
        d.getRealMetrics(m);
        Log.i(TAG, "embedded " + d.getDisplayId() + " dpi=" + m.densityDpi + " target=" + target);
        if (m.densityDpi == target) return;
        try {
            Object wm = windowManager();
            Method set = wm.getClass().getMethod("setForcedDisplayDensityForUser", int.class, int.class, int.class);
            set.invoke(wm, d.getDisplayId(), target, 0 /* user 0 */);
            Log.i(TAG, "density " + d.getDisplayId() + " " + m.densityDpi + " -> " + target);
        } catch (Throwable t) {
            Throwable e = t.getCause() != null ? t.getCause() : t;
            Log.e(TAG, "density set failed: " + e);
        }
    }

    /** 원래 밀도로 되돌리기 (설정에서 끌 때). */
    void resetAll() {
        for (Display d : dm.getDisplays()) {
            if (!isEmbedded(d)) continue;
            try {
                Object wm = windowManager();
                wm.getClass().getMethod("clearForcedDisplayDensityForUser", int.class, int.class)
                        .invoke(wm, d.getDisplayId(), 0);
            } catch (Throwable t) { Log.e(TAG, "reset failed: " + t); }
        }
    }

    static Object windowManager() throws Exception {
        try {
            return Class.forName("android.view.WindowManagerGlobal").getMethod("getWindowManagerService").invoke(null);
        } catch (Throwable t) {
            IBinder b = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "window");
            return Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, b);
        }
    }

    @Override public void onDisplayAdded(int id) {
        // 런처가 창 크기를 잡은 뒤 적용되도록 조금 늦게
        h.postDelayed(() -> apply(dm.getDisplay(id)), 300);
    }

    @Override public void onDisplayChanged(int id) {
        h.postDelayed(() -> apply(dm.getDisplay(id)), 300);
    }

    @Override public void onDisplayRemoved(int id) {}
}
