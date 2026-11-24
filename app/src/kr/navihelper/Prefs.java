package kr.navihelper;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("p", Context.MODE_PRIVATE); }
    static int delaySec(Context c) { return p(c).getInt("delay", 10); }
    static boolean enabled(Context c) { return p(c).getBoolean("on", true); }
    static void set(Context c, int delay, boolean on) { p(c).edit().putInt("delay", delay).putBoolean("on", on).apply(); }

    static boolean inEmbedded(Context c) { return p(c).getBoolean("inEmbedded", false); }
    static void setInEmbedded(Context c, boolean on) { p(c).edit().putBoolean("inEmbedded", on).apply(); }

    static int dpi(Context c) { return p(c).getInt("dpi", 200); }
    static boolean dpiOn(Context c) { return p(c).getBoolean("dpiOn", true); }
    static void setDpi(Context c, int dpi, boolean on) { p(c).edit().putInt("dpi", dpi).putBoolean("dpiOn", on).apply(); }

    static boolean closeAd(Context c) { return p(c).getBoolean("closeAd", true); }
    static void setCloseAd(Context c, boolean on) { p(c).edit().putBoolean("closeAd", on).apply(); }

    /** 재부팅 등으로 접근성 서비스가 꺼지면 자동으로 다시 켤지. */
    static boolean keepOn(Context c) { return p(c).getBoolean("keepOn", true); }
    static void setKeepOn(Context c, boolean on) { p(c).edit().putBoolean("keepOn", on).apply(); }

    static String lastRestored(Context c) { return p(c).getString("restored", ""); }
    static void markRestored(Context c, String why) {
        String t = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.KOREA).format(new java.util.Date());
        p(c).edit().putString("restored", t + " (" + why + ")").commit();
    }

    /** 앱 화면에 보여 줄 최근 동작 몇 줄(원인 찾기용). */
    static String recent(Context c) { return p(c).getString("recent", ""); }
    static void note(Context c, String msg) {
        String t = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.KOREA).format(new java.util.Date());
        String all = t + " " + msg + "\n" + recent(c);
        String[] lines = all.split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(15, lines.length); i++) b.append(lines[i]).append('\n');
        p(c).edit().putString("recent", b.toString()).apply();
    }
}
