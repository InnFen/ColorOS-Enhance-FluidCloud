package dev.lsposed.fluidcloud;

/** Retain the last measurement while capture is suspended; only a real pause requests silence. */
final class MediaWaveEnvelope {
    static final int FRAME_MS = 34;
    private static final float RESUME_RAMP_MS = 120;
    private enum Motion { LIVE, PAUSED, SUSPENDED }

    private final float[] shown=new float[MediaWaveLevels.COUNT];
    private MediaWaveLevels target=MediaWaveLevels.SILENT;
    private int attackMs=45,releaseMs=105;
    private Motion motion = Motion.LIVE;
    private boolean held, skippedEmptySample;
    private float resumeElapsed = RESUME_RAMP_MS;

    void configure(MediaWaveOptions options){attackMs=options.attackMs();releaseMs=options.releaseMs();}
    boolean sample(MediaWaveLevels value) {
        // A newly opened capture may report an empty window. Give it one read
        // before easing retained heights toward a genuinely quiet signal.
        if (motion == Motion.SUSPENDED && !skippedEmptySample
                && value.equals(MediaWaveLevels.SILENT) && !target.equals(MediaWaveLevels.SILENT)) {
            skippedEmptySample = true;
            return false;
        }
        if (motion != Motion.LIVE) resumeElapsed = 0;
        motion = Motion.LIVE;
        boolean changed = !target.equals(value);
        target = value;
        return changed;
    }

    void suspend() {
        if (motion == Motion.SUSPENDED) return;
        motion = Motion.SUSPENDED;
        target = snapshot();
        resumeElapsed = RESUME_RAMP_MS;
    }

    void captureStarting() {
        suspend();
        skippedEmptySample = false;
    }

    void pause(){pause(false);}
    void pause(boolean hold){
        if (motion == Motion.PAUSED && held == hold) return;
        motion = Motion.PAUSED;
        held = hold;
        resumeElapsed = RESUME_RAMP_MS;
        target = hold ? snapshot() : MediaWaveLevels.SILENT;
    }

    void reset() {
        motion = Motion.LIVE;
        resumeElapsed = RESUME_RAMP_MS;
        target = MediaWaveLevels.SILENT;
        java.util.Arrays.fill(shown, 0);
    }
    float at(int index){return shown[index];}
    MediaWaveLevels snapshot(){return new MediaWaveLevels(shown[0],shown[1],shown[2],shown[3],shown[4],shown[5]);}
    boolean moving(){
        for(int i=0;i<shown.length;i++)if(Math.abs(target.at(i)-shown[i])>.002f)return true;
        return false;
    }
    boolean step(float elapsed){
        elapsed=Math.max(1,Math.min(66,elapsed));
        boolean resuming = resumeElapsed < RESUME_RAMP_MS;
        if (resuming) {
            // Ease in the normal response, retaining the configured attack and
            // release once the handoff from the saved frame is complete.
            elapsed = Math.min(FRAME_MS, elapsed);
            float response = Math.min(1, (resumeElapsed + elapsed / 2) / RESUME_RAMP_MS);
            resumeElapsed += elapsed;
            elapsed *= response;
        }
        for(int i=0;i<shown.length;i++){
            float delta=target.at(i)-shown[i];float tau=delta>0?attackMs:releaseMs;
            // Even the fastest steady-state setting must ease in from the saved frame.
            if (resuming) tau = Math.max(20, tau);
            if(Math.abs(delta)>.002f)shown[i]+=delta*(1-(float)Math.exp(-elapsed/tau));
            else shown[i]=target.at(i);
        }
        return moving();
    }
}
