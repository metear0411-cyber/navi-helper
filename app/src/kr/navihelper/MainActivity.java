package kr.navihelper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    TextView st;
    EditText delay, dpi;
    CheckBox on, dpiOn, inEmbedded, keepOn;

    TextView label(LinearLayout l, String s) {
        TextView t = new TextView(this);
        t.setTextSize(18);
        t.setPadding(0, 24, 0, 0);
        t.setText(s);
        l.addView(t);
        return t;
    }

    EditText num(LinearLayout l, int v) {
        EditText e = new EditText(this);
        e.setTextSize(22);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(v));
        l.addView(e);
        return e;
    }

    CheckBox check(LinearLayout l, String s, boolean v) {
        CheckBox c = new CheckBox(this);
        c.setTextSize(20);
        c.setText(s);
        c.setChecked(v);
        l.addView(c);
        return c;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 48, 48, 48);
        st = new TextView(this);
        st.setTextSize(20);
        l.addView(st);

        label(l, "[티맵] 홈 화면에서 몇 초 동안 조작이 없으면 주행 버튼을 누를까요?");
        delay = num(l, Prefs.delaySec(this));
        on = check(l, "티맵 자동 주행 사용", Prefs.enabled(this));
        inEmbedded = check(l, "홈 화면 분할 칸의 티맵에서도 자동 주행 (발열 증가)", Prefs.inEmbedded(this));

        label(l, "[홈 화면 분할 칸] 글자 크기(dpi). 기본 160, 클수록 크게 보임 (추천 190~220)");
        dpi = num(l, Prefs.dpi(this));
        dpiOn = check(l, "분할 칸 dpi 조절 사용", Prefs.dpiOn(this));

        label(l, "[재부팅 대비] 재부팅 뒤 접근성 서비스가 꺼져 있으면 자동으로 다시 켭니다 (adb 권한 필요)");
        keepOn = check(l, "접근성 서비스 자동으로 다시 켜기", Prefs.keepOn(this));

        Button save = new Button(this);
        save.setTextSize(20);
        save.setText("저장");
        save.setOnClickListener(v -> { save(); refresh(); });
        l.addView(save);
        Button acc = new Button(this);
        acc.setTextSize(20);
        acc.setText("접근성 설정 열기");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        l.addView(acc);
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        setContentView(sv);
    }

    static int clamp(String s, int def, int lo, int hi) {
        int d;
        try { d = Integer.parseInt(s.trim()); } catch (Exception e) { d = def; }
        return Math.max(lo, Math.min(hi, d));
    }

    void save() {
        int d = clamp(delay.getText().toString(), 10, 3, 600);
        delay.setText(String.valueOf(d));
        Prefs.set(this, d, on.isChecked());
        Prefs.setInEmbedded(this, inEmbedded.isChecked());
        int p = clamp(dpi.getText().toString(), 200, 120, 320);
        dpi.setText(String.valueOf(p));
        Prefs.setDpi(this, p, dpiOn.isChecked());
        AutoDriveService.densitySettingsChanged(dpiOn.isChecked());
        Prefs.setKeepOn(this, keepOn.isChecked());
    }

    @Override protected void onResume() {
        super.onResume();
        AccEnabler.ensure(this, "app");
        refresh();
    }

    void refresh() {
        boolean svc = AccEnabler.isEnabled(this);
        boolean perm = AccEnabler.hasPermission(this);
        String last = Prefs.lastRestored(this);
        st.setText("접근성 서비스: " + (svc ? "켜짐" : "꺼짐 (아래 버튼으로 켜 주세요)")
                + "\nadb 권한(dpi 조절·접근성 자동 켜기): " + (perm ? "있음" : "없음 (PC에서 adb로 1회 허용 필요, README 참고)")
                + "\n접근성 자동 켜기: " + (Prefs.keepOn(this) ? "사용" : "사용 안 함")
                + (last.isEmpty() ? "" : ", 마지막으로 다시 켠 때 " + last)
                + "\n티맵: " + Prefs.delaySec(this) + "초, " + (Prefs.enabled(this) ? "사용" : "사용 안 함")
                + (Prefs.inEmbedded(this) ? " (전체 화면 + 홈 분할 칸)" : " (전체 화면에서만)")
                + "\n분할 칸 dpi: " + Prefs.dpi(this) + ", " + (Prefs.dpiOn(this) ? "사용" : "사용 안 함"));
    }
}
