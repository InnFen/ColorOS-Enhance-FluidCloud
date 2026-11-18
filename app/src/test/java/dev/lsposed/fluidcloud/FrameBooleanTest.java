package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class FrameBooleanTest {
    @Test public void sameFrameReadsOnceAndAStateEventRechecksImmediately() {
        long[] time={100};boolean[] allowed={true};int[] reads={0};
        var cache=new FrameBoolean(()->time[0]);
        java.util.function.BooleanSupplier read=()->{reads[0]++;return allowed[0];};
        assertTrue(cache.get(read));assertTrue(cache.get(read));assertEquals(1,reads[0]);
        allowed[0]=false;cache.invalidate();assertFalse(cache.get(read));assertEquals(2,reads[0]);
        time[0]++;allowed[0]=true;assertTrue(cache.get(read));assertEquals(3,reads[0]);
    }
    @Test public void outsideAnimationCallbacksEveryReadIsFreshAndFailuresAreNeverCached() {
        var cache=new FrameBoolean(()->Long.MIN_VALUE);boolean[] allowed={true};
        assertTrue(cache.get(()->allowed[0]));allowed[0]=false;assertFalse(cache.get(()->allowed[0]));
        var framed=new FrameBoolean(()->5);
        try {framed.get(()->{throw new IllegalStateException("read failed");});fail();}
        catch(IllegalStateException expected){}
        assertFalse(framed.get(()->false));
    }
}
