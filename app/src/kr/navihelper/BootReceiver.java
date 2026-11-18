package kr.navihelper;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * 부팅·앱 업데이트 때 접근성 서비스를 다시 켠다.
 * FYT가 부팅 직후 조금 늦게 설정을 지우는 경우가 있어 10초·30초·90초·3분 뒤에도 다시 확인한다.
 */
public class BootReceiver extends BroadcastReceiver {
    static final long[] DELAYS_MS = {10_000, 30_000, 90_000, 180_000};

    @Override public void onReceive(Context c, Intent i) {
        String why = i == null || i.getAction() == null ? "boot" : i.getAction().replace("android.intent.action.", "");
        AccEnabler.ensure(c, why);
        schedule(c);
    }

    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        ComponentName svc = new ComponentName(c, RecheckJob.class);
        for (int n = 0; n < DELAYS_MS.length; n++) {
            js.schedule(new JobInfo.Builder(7800 + n, svc)
                    .setMinimumLatency(DELAYS_MS[n])
                    .setOverrideDeadline(DELAYS_MS[n] + 15_000)
                    .build());
        }
    }
}
