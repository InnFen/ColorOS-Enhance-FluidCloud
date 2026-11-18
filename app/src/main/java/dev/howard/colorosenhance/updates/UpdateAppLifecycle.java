package dev.howard.colorosenhance.updates;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;

/** Tracks all module Activities, including manual update and migration pages, without retaining them. */
public final class UpdateAppLifecycle implements Application.ActivityLifecycleCallbacks {
    private final Handler main;
    private final LaunchUpdateCheck checks;
    private int started;
    private final Runnable background;
    public UpdateAppLifecycle(Handler main, LaunchUpdateCheck checks) {
        this.main=main; this.checks=checks;
        background=()->{if(started==0)checks.background();};
    }
    @Override public void onActivityStarted(Activity activity) {
        main.removeCallbacks(background);
        if(started++==0)checks.foreground();
    }
    @Override public void onActivityStopped(Activity activity) {
        if(started>0 && --started==0)main.postDelayed(background,700);
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
