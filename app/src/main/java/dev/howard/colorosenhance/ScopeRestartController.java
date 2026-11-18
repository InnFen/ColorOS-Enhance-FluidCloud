package dev.howard.colorosenhance;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
public final class ScopeRestartController {
    private final Executor worker,main;
    private final Supplier<ScopeRestarter.Outcome> job;
    private final Runnable changed;
    private boolean running;
    private ScopeRestarter.Outcome result;
    public ScopeRestartController(Executor worker,Executor main,Supplier<ScopeRestarter.Outcome> job,Runnable changed) {
        this.worker=worker; this.main=main; this.job=job; this.changed=changed;
    }
    public synchronized boolean start() {
        if(running) return false;
        running=true; result=null; changed.run();
        try { worker.execute(()-> {
            ScopeRestarter.Outcome outcome;
            try { outcome=job.get(); } catch(RuntimeException failure) { outcome=ScopeRestarter.failure(ScopeRestarter.Code.FAILED); }
            ScopeRestarter.Outcome completed=outcome==null ? ScopeRestarter.failure(ScopeRestarter.Code.FAILED) : outcome;
            main.execute(()->complete(completed));
        }); } catch(RuntimeException rejected) { complete(ScopeRestarter.failure(ScopeRestarter.Code.FAILED)); }
        return true;
    }
    private synchronized void complete(ScopeRestarter.Outcome outcome) { running=false; result=outcome; changed.run(); }
    public synchronized boolean running() { return running; }
    public synchronized ScopeRestarter.Outcome result() { return result; }
}
