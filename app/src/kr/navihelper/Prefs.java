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
}
