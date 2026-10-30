package org.btcsma.app;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorService extends JobService {
    private static final int JOB_ID = 200;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private AtomicBoolean stopped = new AtomicBoolean();
    public static void schedule(Context c) {
        JobScheduler jobs = c.getSystemService(JobScheduler.class);
        boolean enabled = false;
        for (int interval : Monitor.INTERVALS) enabled |= Monitor.prefs(c).getBoolean("enabled" + interval, false);
        if (!enabled) { jobs.cancel(JOB_ID); return; }
        if (jobs.getPendingJob(JOB_ID) == null) jobs.schedule(new JobInfo.Builder(JOB_ID,
            new ComponentName(c, MonitorService.class)).setPeriodic(15 * 60 * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        AtomicBoolean jobStopped = new AtomicBoolean();
        stopped = jobStopped;
        task = executor.submit(() -> {
            Monitor.refresh(getApplicationContext(), true);
            if (!jobStopped.get()) jobFinished(params, false);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) {
        stopped.set(true);
        if (task != null) task.cancel(true);
        return true;
    }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
