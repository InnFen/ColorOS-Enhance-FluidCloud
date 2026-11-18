package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWaveLevelsTest {
    @Test public void noiseGateRemainsSilentEvenWithMaximumSensitivity(){
        for(double value:new double[]{0,.005,.01,.015})assertEquals(0,MediaWaveLevels.display(value,500),0);
    }
    @Test public void softCurvePreservesUsableMiddleRangeAndHighEndHeadroom(){
        assertTrue(MediaWaveLevels.display(.25,100)>.4f);assertTrue(MediaWaveLevels.display(.5,100)>.65f);
        assertTrue(MediaWaveLevels.display(.8,100)<.95f);assertEquals(1,MediaWaveLevels.display(1,100),0);
    }
    @Test public void sensitivityIsBoundedAndDoesNotChangeTheSixBarRecord(){
        assertTrue(MediaWaveLevels.display(.25,50)<MediaWaveLevels.display(.25,100));
        assertTrue(MediaWaveLevels.display(.25,100)<MediaWaveLevels.display(.25,200));
        assertTrue(MediaWaveLevels.display(.12,200)<MediaWaveLevels.display(.12,500));
        assertTrue(MediaWaveLevels.display(.25,5)<MediaWaveLevels.display(.25,25));
        assertEquals(MediaWaveLevels.display(.25,5),MediaWaveLevels.display(.25,-1),0);
        assertEquals(MediaWaveLevels.display(.12,500),MediaWaveLevels.display(.12,1000),0);
        assertEquals(1,MediaWaveLevels.display(1000,500),0);
        assertEquals(6,MediaWaveLevels.COUNT);assertEquals(0,MediaWaveLevels.SILENT.at(5),0);
    }
    @Test public void amplitudeResponseIsContinuousRatherThanClippingOrdinarySignals(){
        float previous=0;
        for(int i=1;i<100;i++){
            float value=MediaWaveLevels.display(i/100d,100);assertTrue(value>=previous);assertTrue(value-previous<.03f);
            assertTrue(value<1);previous=value;
        }
    }
}
