package dev.lsposed.fluidcloud;

import java.util.function.BiConsumer;

/** One serialized worker and effect, bounded user capture interval; no UI-thread audio calls. */
final class MediaWaveCapture {
    record Request(int uid,int session,int route,int player) {
        Request(int uid,int session,int route){this(uid,session,route,0);}
        Request { if(uid<0 || session<=0)throw new IllegalArgumentException("A private playback session is required"); }
    }
    interface Scheduler {
        void post(Runnable action,long delay);void remove(Runnable action);
        default long now(){return System.nanoTime()/1_000_000L;}
    }
    interface Source extends AutoCloseable {
        MediaWaveLevels read() throws Exception;
        /** No new audible-time frame yet; keep the last presentation, not a zero sample. */
        default boolean buffering(){return false;}
        @Override void close();
    }
    enum Route { SAFE, WAIT, UNSAFE }
    interface Backend {
        boolean safe(Request request) throws Exception;
        default Route route(Request request)throws Exception{return safe(request)?Route.SAFE:Route.WAIT;}
        Source open(Request request) throws Exception;
    }
    /** A bounded retry is permitted only for transient native initialization/readiness. */
    static final class NotReady extends Exception {
        NotReady(Throwable cause){super(cause);}
        NotReady(String reason){super(reason);}
    }
    private static final int[] RETRY_MS={180,360,720,1440,2000};
    private final Scheduler worker;
    private final Backend backend;
    private final BiConsumer<Long,MediaWaveLevels> output;
    private final BiConsumer<Long,String> fallback;
    private volatile long generation;
    private Request requested,failed;
    private Request budgetKey;
    private int retries;
    private long healthySince=-1;
    private long bufferingSince=-1;
    private Source source;
    private long sourceTicket;
    private long fallbackTicket;
    private Runnable pending;
    private volatile int intervalMs = MediaWaveOptions.DEFAULTS.intervalMs();
    void interval(int value) {
        intervalMs = Math.max(MediaWaveOptions.INTERVAL_MIN_MS, Math.min(MediaWaveOptions.INTERVAL_MAX_MS, value));
    }
    MediaWaveCapture(Scheduler worker,Backend backend,BiConsumer<Long,MediaWaveLevels> output,BiConsumer<Long,String> fallback){
        this.worker=worker;this.backend=backend;this.output=output;this.fallback=fallback;
    }
    // Called on main. Invalidate an in-flight route check before posting its cancellation.
    long request(Request next){
        long ticket=++generation;
        worker.post(()->begin(ticket,next),0);return ticket;
    }
    long generation(){return generation;}
    private void begin(long ticket,Request next){
        if(ticket!=generation)return;
        stop();requested=next;
        if(next==null)return;
        if(next.equals(failed)){notifyFallback(ticket,"unavailable");return;}
        if(!next.equals(budgetKey)){budgetKey=next;retries=0;}
        acquire(ticket,next);
    }
    private void acquire(long ticket,Request next){
        if(ticket!=generation)return;
        try{
            Route route=backend.route(next);
            if(ticket!=generation)return;
            if(route==Route.UNSAFE){
                // Restore OEM immediately, but a startup route can later settle on
                // PCM without changing its player key. Only read-only checks may
                // continue within the same finite budget; never open while unsafe.
                stop();notifyFallback(ticket,"unsafe audio route");retry(ticket,next,"unsafe audio route");return;
            }
            if(route!=Route.SAFE){retry(ticket,next,"audio route not ready");return;}
            if(ticket!=generation)return;
            source=backend.open(next);sourceTicket=ticket;
            if(source==null)throw new IllegalStateException("No visualizer");
            if(ticket!=generation){stop();return;}
            sample(ticket);
        }catch(NotReady error){stop();retry(ticket,next,"audio engine not ready");}
        catch(Exception|LinkageError error){fail(ticket,"audio visualization unavailable");}
    }
    private void retry(long ticket,Request next,String reason){
        if(ticket!=generation)return;
        if(retries>=RETRY_MS.length){fail(ticket,reason);return;}
        // No background polling after this short startup window. Every attempt must
        // positively pass the route check again before it may create a private effect.
        int delay=RETRY_MS[retries++];pending=()->acquire(ticket,next);worker.post(pending,delay);
    }
    private void sample(long ticket){
        if(ticket!=generation){if(sourceTicket==ticket)stop();return;}
        try{
            MediaWaveLevels levels=source.read();
            if(levels==null)throw new IllegalStateException("No measurement");
            if(ticket!=generation){stop();return;}
            long now=worker.now();
            if(source.buffering()){
                if(bufferingSince<0)bufferingSince=now;
                if(now-bufferingSince>3500)throw new IllegalStateException("Capture timeline did not settle");
                pending=()->sample(ticket);worker.post(pending,intervalMs);return;
            }
            bufferingSince=-1;
            if(healthySince<0)healthySince=now;
            else if(now-healthySince>=60000){retries=0;healthySince=now;}
            // A later genuine failure must restore OEM again after custom output
            // recovered; repeated checks within one OEM phase are notified once.
            fallbackTicket=0;output.accept(ticket,levels);
            pending=()->sample(ticket);worker.post(pending,intervalMs);
        }catch(NotReady error){
            // Do not re-enable a shared effect in place. Release this handle, recheck
            // the actual route, and use the same finite budget as startup readiness.
            stop();retry(ticket,requested,"audio data not ready");
        }catch(Exception|LinkageError error){fail(ticket,"audio data unavailable");}
    }
    private void fail(long ticket,String reason){
        if(ticket==generation){failed=requested;stop();notifyFallback(ticket,reason);}
        else if(sourceTicket==ticket)stop();
    }
    private void notifyFallback(long ticket,String reason){
        if(ticket==generation&&fallbackTicket!=ticket){fallbackTicket=ticket;fallback.accept(ticket,reason);}
    }
    private void stop(){
        healthySince=-1;
        bufferingSince=-1;
        if(pending!=null){worker.remove(pending);pending=null;}
        Source previous=source;source=null;
        if(previous!=null)try{previous.close();}catch(RuntimeException|LinkageError ignored){/* Already failing closed. */}
    }
}
