package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class CapsuleMotionTest {
    @Test public void panelHideUsesTheFastNativePathAndContractsBeforeItsFinalFiftyMilliseconds(){
        CapsuleMotion motion=new CapsuleMotion();motion.target(false);motion.advance(100);
        assertEquals(1d/3,motion.current().body(),.000001);assertEquals(0,motion.current().alpha(),0);assertEquals(1,motion.current().blur(),0);
        motion.advance(50);assertEquals(0,motion.current().body(),0);assertEquals(1,motion.current().dot(),0);
        motion.advance(25);assertEquals(.5,motion.current().dot(),.000001);
        motion.advance(25);assertEquals(CapsuleMotion.HIDDEN,motion.current());assertFalse(motion.moving());
    }
    @Test public void nativeExpansionHasDotGrowthContentFocusAndASeparateReboundTail(){
        CapsuleMotion motion=new CapsuleMotion();motion.snap(false);motion.target(true);motion.advance(50);
        assertEquals(1,motion.current().dot(),0);assertEquals(0,motion.current().body(),.000001);assertEquals(0,motion.current().alpha(),0);
        motion.advance(300);assertEquals(1,motion.current().body(),0);assertEquals(1,motion.current().extra(),0);assertEquals(1.1,motion.current().scale(),0);
        motion.advance(250);assertTrue(motion.current().extra()>0);assertTrue(motion.current().scale()>1);
        motion.advance(250);assertEquals(CapsuleMotion.FULL,motion.current());assertFalse(motion.moving());
    }
    @Test public void everyInterruptionBeginsAtTheExactCurrentShapeContentAndBlur(){
        for(int delay:new int[]{8,35,80,180,330,550,800}){
            CapsuleMotion motion=new CapsuleMotion();motion.snap(false);motion.target(true);motion.advance(delay);
            var before=motion.current();motion.target(false);assertEquals(before,motion.current());
            motion.advance(13);before=motion.current();motion.target(true);assertEquals(before,motion.current());
            motion.advance(1000);assertEquals(CapsuleMotion.FULL,motion.current());
        }
    }
    @Test public void fastReversalsKeepAllChannelsFiniteAndNeverResetToAnEndpoint(){
        CapsuleMotion motion=new CapsuleMotion();
        for(int i=0;i<200;i++){
            var previous=motion.current();motion.target((i&1)!=0);assertEquals(previous,motion.current());
            motion.advance(4+i%9*9);var pose=motion.current();
            for(double value:new double[]{pose.body(),pose.dot(),pose.extra(),pose.alpha(),pose.blur(),pose.moon()}){
                assertTrue(Double.isFinite(value));assertTrue(value>=0 && value<=1);
            }
            assertTrue(pose.scale()>=.1-.000001 && pose.scale()<=1.1+.000001);
        }
        motion.target(false);motion.advance(1000);assertEquals(CapsuleMotion.HIDDEN,motion.current());assertFalse(motion.moving());
    }
    @Test public void expansionContentDoesNotFlashBeforeTheNativeFocusDelay(){
        CapsuleMotion motion=new CapsuleMotion();motion.snap(false);motion.target(true);motion.advance(99);
        assertEquals(0,motion.current().alpha(),0);assertEquals(1,motion.current().blur(),0);
        motion.advance(80);assertTrue(motion.current().alpha()>0);assertTrue(motion.current().blur()<1);
    }
}
