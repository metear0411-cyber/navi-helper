package kr.navihelper;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

/**
 * FYT 시스템이 재부팅 때 접근성 설정을 초기화해서 '티맵 자동주행'이 꺼지는 문제 대응.
 * WRITE_SECURE_SETTINGS 권한(dpi 조절용으로 이미 adb로 허용)이 있으면
 * 설정값에 이 앱의 서비스를 다시 넣어 켠다. 다른 앱의 접근성 서비스는 그대로 둔다.
 */
final class AccEnabler {
    static final String TAG = "TmapAuto";

    static String component(Context c) {
        return new ComponentName(c, AutoDriveService.class).flattenToString();
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0;
    }

    static boolean isEnabled(Context c) {
        String en = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (en == null) return false;
        String me = component(c);
        String shortMe = c.getPackageName() + "/.AutoDriveService";
        for (String s : en.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(shortMe)) {
                return Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
            }
        }
        return false;
    }

    /** 꺼져 있으면 다시 켠다. 켰으면 true. */
    static boolean ensure(Context c, String why) {
        if (!Prefs.keepOn(c) || isEnabled(c)) return false;
        if (!hasPermission(c)) {
            Log.w(TAG, "accessibility off, but no WRITE_SECURE_SETTINGS (" + why + ")");
            Prefs.note(c, "접근성이 꺼져 있는데 adb 권한이 없어 못 켬 (" + why + ")");
            return false;
        }
        try {
            String en = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String me = component(c);
            StringBuilder sb = new StringBuilder();
            if (en != null) {
                for (String s : en.split(":")) {
                    if (s.isEmpty() || s.startsWith(c.getPackageName() + "/")) continue;
                    if (sb.length() > 0) sb.append(':');
                    sb.append(s);
                }
            }
            if (sb.length() > 0) sb.append(':');
            sb.append(me);
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, sb.toString());
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Prefs.markRestored(c, why);
            Prefs.note(c, "접근성이 꺼져 있어서 다시 켬 (" + why + ")");
            Log.i(TAG, "accessibility re-enabled (" + why + ")");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "re-enable failed: " + t);
            return false;
        }
    }
}
