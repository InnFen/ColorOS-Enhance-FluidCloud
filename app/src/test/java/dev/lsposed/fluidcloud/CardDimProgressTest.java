package dev.lsposed.fluidcloud;
import org.junit.Test;
import static org.junit.Assert.*;
public class CardDimProgressTest {
    @Test public void cameraAndExpandedEndpointsMatchConfiguredIntensity(){
        assertEquals(0,CardDimProgress.alpha(100,1000,100,1,60),0);
        assertEquals(.6f,CardDimProgress.alpha(1000,1000,100,1,60),.0001f);
        assertEquals(.3f,CardDimProgress.alpha(550,1000,100,1,60),.0001f);
    }
    @Test public void forwardReverseAndRepeatedInterruptionsAlwaysUseCurrentGeometry(){
        for(float width:new float[]{100,300,700,450,750,200,550,1000,500,100})
            assertEquals(Math.max(0,(width-100)/900)*.8f,CardDimProgress.alpha(width,1000,100,1,80),.0001f);
    }
    @Test public void nativeAlphaAndZeroIntensityDoNotLeaveAVisibleBackdrop(){
        assertEquals(.2f,CardDimProgress.alpha(1000,1000,100,.5f,40),.0001f);
        assertEquals(0,CardDimProgress.alpha(1000,1000,100,0,60),0);
        assertEquals(0,CardDimProgress.alpha(1000,1000,100,1,0),0);
    }
    @Test public void unknownGeometryIsTransparentAndSpringOvershootCannotOverdarken(){
        assertEquals(0,CardDimProgress.alpha(Float.NaN,1000,100,1,60),0);
        assertEquals(0,CardDimProgress.alpha(100,0,100,1,60),0);
        assertEquals(.6f,CardDimProgress.alpha(1050,1000,100,1,60),.0001f);
    }
}
