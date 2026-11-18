package dev.lsposed.fluidcloud;

import android.graphics.RectF;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CapsulePresentationGeometryTest {
    @Test public void onePathClipsAtNativeHeightWithoutSquashingContentAndTouchUsesItsExactInverse(){
        RectF content=new RectF(300,25,900,130),camera=new RectF(600,25,670,130);
        for(int i=0;i<=100;i++){
            var geometry=CapsulePresentationGeometry.resolve(content,camera,i/100d);assertNotNull(geometry);
            RectF mapped=new RectF(content);geometry.forward().mapRect(mapped);
            assertEquals(content.width(),mapped.width(),.001);assertEquals(content.height(),mapped.height(),.001);
            assertEquals(content.top,geometry.bounds().top,.001);assertEquals(content.bottom,geometry.bounds().bottom,.001);
            assertEquals(geometry.bounds().centerX(),mapped.centerX(),.001);
            float[] point={360,80};geometry.forward().mapPoints(point);geometry.inverse().mapPoints(point);
            assertEquals(360,point[0],.001);assertEquals(80,point[1],.001);
        }
        assertEquals(content.height(),CapsulePresentationGeometry.resolve(content,camera,0).bounds().width(),.001);
        assertEquals(content,CapsulePresentationGeometry.resolve(content,camera,1).bounds());
    }
    @Test public void aSafeInsetRectangleCannotMoveTheCollapseUpOrTurnItIntoAVerticalBar(){
        RectF content=new RectF(458,31,814,136),cutout=new RectF(600,0,672,141);
        var collapsed=CapsulePresentationGeometry.resolve(content,cutout,0).bounds();
        assertEquals(31,collapsed.top,0);assertEquals(136,collapsed.bottom,0);
        assertEquals(collapsed.height(),collapsed.width(),0);assertEquals(636,collapsed.centerX(),0);
    }
    @Test public void missingCameraCanRenderNativeSizeButCannotInventAnAnimationAnchor(){
        RectF content=new RectF(50,20,350,100);
        assertNotNull(CapsulePresentationGeometry.resolve(content,null,1));
        assertNull(CapsulePresentationGeometry.resolve(content,null,.5));assertNull(CapsulePresentationGeometry.resolve(content,content,Double.NaN));
        assertNull(CapsulePresentationGeometry.resolve(new RectF(0,0,Float.POSITIVE_INFINITY,20),content,1));
    }
    @Test public void nativeProfileKeepsContentUniformWhileInputTracksTheOuterMorph(){
        RectF content=new RectF(450,30,820,135),dot=new RectF(602,50,668,116);
        var profile=new CapsulePresentationGeometry.Profile(dot,28,31.5f);
        var pose=new CapsuleMotion.Pose(.5,1,.4,.6,.5,.5,.5);
        var geometry=CapsulePresentationGeometry.resolve(content,profile,pose);assertNotNull(geometry);
        RectF outer=new RectF(content);geometry.forward().mapRect(outer);
        assertEquals(geometry.bounds().left,outer.left,.001);assertEquals(geometry.bounds().bottom,outer.bottom,.001);
        RectF pixels=new RectF(content);geometry.contentForward().mapRect(pixels);
        assertEquals(.6,pixels.width()/content.width(),.0001);assertEquals(.6,pixels.height()/content.height(),.0001);
        assertEquals(15.75,geometry.blurRadius(),.001);assertEquals(.5,geometry.contentAlpha(),0);
        assertNull(CapsulePresentationGeometry.resolve(content,profile,CapsuleMotion.HIDDEN));
    }
    @Test public void nativeExpansionCurveMatchesTheFirmwarePathInterpolator(){
        var nativeCurve=new android.view.animation.PathInterpolator(.3f,0,.1f,1);
        for(int i=0;i<=100;i++)assertEquals(nativeCurve.getInterpolation(i/100f),CapsuleMotion.curve(i/100d),.01);
    }
}
