package dev.lsposed.fluidcloud;

/** Audio ownership is temporary; a known six-bar presentation survives pause/card transitions. */
final class MediaWaveContinuity {
    private Object source;
    private boolean playing,played,custom,failed;
    private int epoch;
    void source(Object value){
        if(java.util.Objects.equals(source,value))return;
        source=value;playing=false;played=false;custom=false;failed=false;epoch++;
    }
    void playback(boolean value){
        if(value && !playing){epoch++;failed=false;} // A real resume gets a fresh safety check.
        played|=value;
        playing=value;
        if(source!=null && !value && !failed)custom=true;
    }
    boolean playback(boolean value,boolean terminal){
        // A newly opened media session can advertise NONE/STOPPED before its first
        // playback. That is an idle presentation, not an outgoing played capsule.
        boolean ending=terminal && played;playback(value);return ending;
    }
    void sampled(){if(source!=null){custom=true;failed=false;}}
    void fallback(){custom=false;failed=true;}
    boolean custom(){return source!=null && custom && !failed;}
    boolean failed(){return failed;}
    int epoch(){return epoch;}
    void clear(){source=null;playing=false;played=false;custom=false;failed=false;epoch++;}
}
