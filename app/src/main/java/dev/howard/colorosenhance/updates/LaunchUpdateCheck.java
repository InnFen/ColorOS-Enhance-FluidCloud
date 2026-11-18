package dev.howard.colorosenhance.updates;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Main-thread state: one silent check per foreground visit, shared by all settings pages. */
public final class LaunchUpdateCheck {
    private final Executor worker, main;
    private final Supplier<GitHubUpdateClient.Result> job;
    private final long installedCode;
    private final Runnable changed;
    private boolean foreground, checked, running;
    private ReleaseInfo pending;

    public LaunchUpdateCheck(Executor worker, Executor main, Supplier<GitHubUpdateClient.Result> job,
                             long installedCode, Runnable changed) {
        this.worker=worker; this.main=main; this.job=job; this.installedCode=installedCode; this.changed=changed;
    }
    public void foreground() {
        if(foreground)return;
        foreground=true; checked=false; pending=null;
    }
    public void background() {
        foreground=false; checked=false; pending=null; changed.run();
    }
    public void check() {
        if(!foreground || checked)return;
        checked=true;
        // A quick exit/re-entry can reuse the still-running request rather than overlap requests.
        if(running)return;
        running=true;
        try {
            worker.execute(()->{
                GitHubUpdateClient.Result result;
                try { result=job.get(); } catch(RuntimeException unavailable) { result=null; }
                GitHubUpdateClient.Result completed=result;
                main.execute(()->complete(completed));
            });
        } catch(RuntimeException unavailable) { complete(null); }
    }
    private void complete(GitHubUpdateClient.Result result) {
        running=false;
        if(!foreground || !checked)return;
        ReleaseInfo release=result==null?null:result.release();
        if(release!=null && release.newerThan(installedCode)) { pending=release; changed.run(); }
    }
    public ReleaseInfo pending() { return foreground?pending:null; }
    public void dismiss() { pending=null; changed.run(); }
}
