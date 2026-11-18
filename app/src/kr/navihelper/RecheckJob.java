package kr.navihelper;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** 부팅 뒤 몇 차례 접근성 서비스가 꺼졌는지 확인하고 다시 켠다. */
public class RecheckJob extends JobService {
    @Override public boolean onStartJob(JobParameters p) {
        AccEnabler.ensure(this, "recheck+" + (p.getJobId() - 7800));
        return false;
    }

    @Override public boolean onStopJob(JobParameters p) { return false; }
}
