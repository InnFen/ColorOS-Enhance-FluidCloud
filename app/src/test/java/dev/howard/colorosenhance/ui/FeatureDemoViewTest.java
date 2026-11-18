package dev.howard.colorosenhance.ui;

import static org.junit.Assert.*;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class FeatureDemoViewTest {
    private ActivityController<Activity> controller;
    @Before public void setUp(){ReflectionHelpers.setStaticField(ValueAnimator.class,"sDurationScale",1f);}
    @After public void tearDown(){if(controller!=null)controller.close();ReflectionHelpers.setStaticField(ValueAnimator.class,"sDurationScale",1f);}
    private FeatureDemoView mount(String kind){
        controller=Robolectric.buildActivity(Activity.class).setup().visible();
        var view=new FeatureDemoView(controller.get(),kind);controller.get().setContentView(view);
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(404,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,720,404);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();return view;
    }
    private static ValueAnimator animator(FeatureDemoView view){return ReflectionHelpers.getField(view,"animator");}
    @Test public void oneDemonstrationCompletesWithoutAnIdleAnimationLoop(){
        var view=mount("camera");view.play();assertNotNull(animator(view));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertNull(animator(view));assertEquals(1f,(float)ReflectionHelpers.getField(view,"progress"),0f);
    }
    @Test public void hidingOrDetachingCancelsAnimationAndPendingReplay(){
        var view=mount("media");view.play();assertNotNull(animator(view));
        view.onVisibilityAggregated(false);assertNull(animator(view));
        view.play();assertNotNull(animator(view));
        controller.get().setContentView(new android.view.View(controller.get()));
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(animator(view));
    }
    @Test public void disabledSystemAnimationsAndStaticBatteryPreviewNeverStartAClock(){
        var view=mount("camera");ReflectionHelpers.setStaticField(ValueAnimator.class,"sDurationScale",0f);view.play();
        assertNull(animator(view));assertEquals(1f,(float)ReflectionHelpers.getField(view,"progress"),0f);
        ReflectionHelpers.setStaticField(ValueAnimator.class,"sDurationScale",1f);
        controller.close();controller=null;var battery=mount("battery");battery.play();assertNull(animator(battery));
    }
    @Test public void nativeHostCannotInvertTheAlreadyThemedCameraAndMaterial(){
        var view=mount("camera");assertFalse(view.isForceDarkAllowed());
    }
    @Test public void orientationPreviewUsesSavedLandscapeRulesAndInheritance(){
        var view=mount("scenes");view.stop();
        Map<String,Object> values=new HashMap<>(Map.of("unbind_status_bar",true,"recents",true,"landscape_recents_shrink",true));
        view.presentation("cloud_portrait");view.values(values);Bitmap portrait=midpoint(view);
        view.presentation("cloud_landscape");Bitmap landscape=midpoint(view);
        // Sample the capsule body, away from the sample album art and physical camera hole.
        assertEquals(android.graphics.Color.BLACK,at(portrait,160,26));
        assertNotEquals(android.graphics.Color.BLACK,at(landscape,160,26));
        values.put("landscape_follow_portrait",true);view.values(values);Bitmap inherited=midpoint(view);
        assertEquals(at(portrait,160,26),at(inherited,160,26));
        assertEquals(4,values.size()); // Presentation never mutates caller-owned settings.
        portrait.recycle();landscape.recycle();inherited.recycle();
    }
    @Test public void finishedCameraPreviewKeepsCardVisibleAndOnlyMovesItsBackgroundHeader(){
        var view=mount("camera");view.stop();view.values(Map.of("camera_card_expansion",false));
        Bitmap stock=frame(view,1);view.values(Map.of("camera_card_expansion",true));Bitmap camera=frame(view,1);
        // First-card material reaches the camera only when enabled. Media content remains safe.
        assertNotEquals(at(stock,150,35),at(camera,150,35));
        assertEquals(at(stock,51,71),at(camera,51,71));
        assertNotEquals(at(camera,51,71),at(camera,80,115));
        assertTrue(view.getContentDescription().toString().contains("模拟预览"));
        stock.recycle();camera.recycle();
    }
    @Test public void onlySelectedIconsFadeAndTheMasterOffRestoresTheSystemExample(){
        var view=mount("icons");view.stop();view.values(Map.of("shade_only.battery",true));Bitmap hidden=frame(view,0);
        view.values(Map.of("shade_only.battery",true,"enabled",false));Bitmap system=frame(view,0);
        assertNotEquals(at(hidden,325,26),at(system,325,26));
        assertRegionEquals(hidden,system,280,22,292,32); // Unselected Wi-Fi is never hidden.
        view.values(Map.of("shade_only.battery",true));Bitmap expanded=frame(view,1);
        assertEquals(android.graphics.Color.rgb(239,241,245),at(expanded,325,38));
        hidden.recycle();system.recycle();expanded.recycle();
    }
    @Test public void pausedSixBandsCanFreezeWithoutStartingAudioOrASecondClock(){
        var view=mount("media");view.stop();
        view.values(Map.of("realtime_media_wave",true,"keep_paused_music",true,"wave_hold_paused",true));
        Bitmap first=frame(view,.53f),later=frame(view,.63f);
        assertRegionEquals(first,later,220,83,290,116);
        view.values(Map.of("realtime_media_wave",true,"keep_paused_music",true,"wave_hold_paused",false));Bitmap cleared=frame(view,.63f);
        assertNotEquals(at(later,225,95),at(cleared,225,95));assertNull(animator(view));
        first.recycle();later.recycle();cleared.recycle();
    }
    @Test public void editedBandsRedistributeTheSameSyntheticSignal() {
        var view = mount("media");
        view.stop();
        view.values(Map.of("realtime_media_wave", true));
        Bitmap defaults = frame(view, .2f);
        view.values(Map.of("realtime_media_wave", true, "wave_band_edges", "20,21,22,23,24,25,24000"));
        Bitmap remapped = frame(view, .2f);
        assertNotEquals(at(defaults, 225, 95), at(remapped, 225, 95));
        assertEquals(at(defaults, 225, 99), at(remapped, 225, 99));
        defaults.recycle();
        remapped.recycle();
    }
    @Test public void independentBackgroundDimmingLeavesAodClockWhite(){
        var view=mount("aod");view.stop();view.values(Map.of("dim_enabled",false,"dim",100));Bitmap plain=frame(view,1);
        view.values(Map.of("dim_enabled",true,"dim",100));Bitmap dimmed=frame(view,1);
        assertNotEquals(at(plain,200,120),at(dimmed,200,120));
        assertEquals(android.graphics.Color.BLACK,at(dimmed,200,120));
        int clockWhite=android.graphics.Color.rgb(239,241,245),plainPixels=0,dimmedPixels=0;
        for(int x=111;x<242;x++)for(int y=25;y<65;y++){
            if(at(plain,x,y)==clockWhite)plainPixels++;
            if(at(dimmed,x,y)==clockWhite)dimmedPixels++;
        }
        assertTrue(plainPixels>30);assertEquals(plainPixels,dimmedPixels);plain.recycle();dimmed.recycle();
    }
    @Test public void movingSimulationsNeverPaintOutsideTheScreenOrOverTheCaptionGap(){
        var view=mount("camera");view.stop();
        for(String kind:new String[]{"camera","scenes","media","icons","capsule","aod","battery"}){
            var example=new FeatureDemoView(controller.get(),kind);
            example.measure(android.view.View.MeasureSpec.makeMeasureSpec(720,android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(404,android.view.View.MeasureSpec.EXACTLY));example.layout(0,0,720,404);
            example.values(Map.of("camera_card_expansion",true,"realtime_media_wave",true,"unbind_status_bar",true,"recents_shrink",true));
            for(float fraction:new float[]{.18f,.4f,.53f,.8f,1}){
                Bitmap bitmap=frame(example,fraction);
                assertEquals(kind,0,at(bitmap,7,80));assertEquals(kind,0,at(bitmap,180,170));
                assertEquals(kind,0,at(bitmap,11,9));bitmap.recycle();
            }
            assertNull(animator(example));
        }
    }
    private static Bitmap midpoint(FeatureDemoView view){
        return frame(view,.5f);
    }
    private static Bitmap frame(FeatureDemoView view,float fraction){
        ReflectionHelpers.setField(view,"progress",fraction);
        assertTrue(view.getWidth()>0);assertTrue(view.getHeight()>0);
        // Window layout may replace the test's initial size; sample the illustration's
        // logical coordinates using its actual scale rather than assuming a screen size.
        var bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));return bitmap;
    }
    private static void assertRegionEquals(Bitmap first,Bitmap second,int left,int top,int right,int bottom){
        for(int x=left;x<right;x++)for(int y=top;y<bottom;y++)assertEquals("pixel "+x+","+y,at(first,x,y),at(second,x,y));
    }
    private static int at(Bitmap bitmap,float x,float y){return bitmap.getPixel(Math.round(bitmap.getWidth()*x/360),Math.round(bitmap.getHeight()*y/202));}
}
