package dev.lsposed.fluidcloud;

import java.util.function.Consumer;

/** Optional aggregate timing only; no audio buffers, metadata, source identifiers or timer. */
final class MediaWaveTrace {
    private final Consumer<String> output;
    private long began=-1,lastSample=-1,lastFrame=-1,sampleGap,frameGap;
    private int samples,changed,frames,draws,tails;
    private MediaWaveLevels previous;
    MediaWaveTrace(Consumer<String> output){this.output=output;}
    void sample(long now,MediaWaveLevels value){
        if(lastSample>=0)sampleGap=Math.max(sampleGap,now-lastSample);lastSample=now;
        samples++;if(!value.equals(previous))changed++;previous=value;report(now);
    }
    void frame(long now){
        if(lastFrame>=0)frameGap=Math.max(frameGap,now-lastFrame);lastFrame=now;frames++;report(now);
    }
    void draw(long now,boolean tail){draws++;if(tail)tails++;report(now);}
    private void report(long now){
        if(began<0){began=now;return;}
        if(now-began<2000)return;
        output.accept("Media wave timing ms="+(now-began)+" samples="+samples+" changed="+changed
                +" frames="+frames+" draws="+draws+" tails="+tails+" sampleGap="+sampleGap+" frameGap="+frameGap);
        began=now;samples=changed=frames=draws=tails=0;sampleGap=frameGap=0;
    }
}
