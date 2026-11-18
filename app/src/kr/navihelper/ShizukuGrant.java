package kr.navihelper;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

/**
 * PC 없이 WRITE_SECURE_SETTINGS 권한을 받는다.
 * Shizuku(무선 디버깅으로 네비가 자기 자신에 붙는 앱)가 켜져 있으면
 * 그 권한으로 `pm grant <이 앱> WRITE_SECURE_SETTINGS`를 한 번 실행한다.
 * 한 번 받은 권한은 재부팅해도 남으므로, 그 뒤로는 Shizuku를 지워도 된다.
 */
final class ShizukuGrant {
    static final String TAG = "TmapAuto";
    static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    static final int REQ = 7870;

    interface Result { void done(boolean ok, String msg); }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(SHIZUKU_PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    static void start(Context c, Result r) {
        Handler main = new Handler(Looper.getMainLooper());
        Result ui = (ok, msg) -> main.post(() -> r.done(ok, msg));
        if (AccEnabler.hasPermission(c)) { ui.done(true, "이미 권한이 있습니다."); return; }
        if (!installed(c)) {
            ui.done(false, "Shizuku 앱이 없습니다. 아래 'Shizuku 설치' 버튼으로 먼저 설치해 주세요.");
            return;
        }
        if (!running()) {
            ui.done(false, "Shizuku가 실행 중이 아닙니다. Shizuku 앱을 열어 '무선 디버깅으로 시작'을 해 주세요. (README 참고)");
            return;
        }
        if (Shizuku.isPreV11()) { ui.done(false, "Shizuku 버전이 너무 오래됐습니다. 업데이트해 주세요."); return; }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            grant(c, ui);
            return;
        }
        Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
            @Override public void onRequestPermissionResult(int code, int result) {
                if (code != REQ) return;
                Shizuku.removeRequestPermissionResultListener(this);
                if (result == PackageManager.PERMISSION_GRANTED) grant(c, ui);
                else ui.done(false, "Shizuku 창에서 '허용'을 눌러야 합니다. 다시 시도해 주세요.");
            }
        });
        Shizuku.requestPermission(REQ);
    }

    /** Shizuku의 셸 권한으로 pm grant 실행. */
    static void grant(Context c, Result ui) {
        new Thread(() -> {
            String err = "";
            try {
                Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true);
                Process p = (Process) m.invoke(null, new Object[]{
                        new String[]{"pm", "grant", c.getPackageName(), "android.permission.WRITE_SECURE_SETTINGS"}, null, null});
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                p.waitFor();
                err = sb.toString().trim();
            } catch (Throwable t) {
                Throwable e = t.getCause() != null ? t.getCause() : t;
                err = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e(TAG, "shizuku grant", e);
            }
            boolean ok = AccEnabler.hasPermission(c);
            if (ok) {
                AccEnabler.ensure(c, "shizuku");   // 받자마자 접근성도 켠다
                ui.done(true, "권한을 받았습니다. 이제 Shizuku는 꺼도(지워도) 됩니다.");
            } else {
                ui.done(false, "권한을 받지 못했습니다. " + err);
            }
        }).start();
    }
}
