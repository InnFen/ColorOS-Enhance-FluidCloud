package dev.lsposed.fluidcloud;

import java.util.Arrays;
import java.util.function.Consumer;

/** Debug-only freshness counters. Never emits audio buffers, hashes or source identities. */
final class MediaWaveInputTrace {
    private final Consumer<String> output;
    private byte[] previous;
    private byte[] previousWaveform;
    private MediaWaveLevels levels;
    private long began=-1,lastRead=-1,rawChangedAt,levelsChangedAt,waveChangedAt;
    private long gap,rawHold,levelsHold,readTime,waveHold;
    private int reads,rawChanges,levelChanges,empty,clipped,waveChanges,waveErrors;
    MediaWaveInputTrace(Consumer<String> output){this.output=output;}
    void read(long now,long duration,byte[] bytes,byte[] waveform,MediaWaveLevels next,MediaWaveOptions options){
        if(began<0)began=now;
        if(lastRead>=0)gap=Math.max(gap,now-lastRead);lastRead=now;
        reads++;readTime=Math.max(readTime,duration);
        if(previous==null || !Arrays.equals(previous,bytes)){
            rawChanges++;rawChangedAt=now;
            if(previous==null || previous.length!=bytes.length)previous=bytes.clone();
            else System.arraycopy(bytes,0,previous,0,bytes.length);
        }else rawHold=Math.max(rawHold,now-rawChangedAt);
        if(waveform==null)waveErrors++;
        else if(previousWaveform==null || !Arrays.equals(previousWaveform,waveform)){
            waveChanges++;waveChangedAt=now;
            if(previousWaveform==null || previousWaveform.length!=waveform.length)previousWaveform=waveform.clone();
            else System.arraycopy(waveform,0,previousWaveform,0,waveform.length);
        }else waveHold=Math.max(waveHold,now-waveChangedAt);
        if(!next.equals(levels)){levelChanges++;levelsChangedAt=now;levels=next;}
        else levelsHold=Math.max(levelsHold,now-levelsChangedAt);
        if(next.equals(MediaWaveLevels.SILENT))empty++;
        for(int i=0;i<MediaWaveLevels.COUNT;i++)if(next.at(i)>=.999f)clipped++;
        if(now-began<2000)return;
        output.accept("Media wave input ms="+(now-began)+" reads="+reads+" rawChanged="+rawChanges
                +" levelsChanged="+levelChanges+" rawHold="+rawHold+" levelsHold="+levelsHold
                +" readGap="+gap+" readTime="+readTime+" empty="+empty+" clippedBands="+clipped
                +" waveChanged="+waveChanges+" waveHold="+waveHold+" waveErrors="+waveErrors
                +" interval="+options.intervalMs()+" sensitivity="+options.sensitivityPercent()
                +" attack="+options.attackMs()+" release="+options.releaseMs());
        began=now;reads=rawChanges=levelChanges=empty=clipped=waveChanges=waveErrors=0;gap=rawHold=levelsHold=readTime=waveHold=0;
    }
}
