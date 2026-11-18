package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Region;
import android.graphics.Rect;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayDeque;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeCapsuleSourceTest {
    ActivityController<Activity> activity;
    FrameLayout root;
    RecordingHost host;
    FrameLayout nativeRoot;
    CapsuleView pill;
    View window;
    NativeCapsuleSource source;
    Region region;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        Context context = activity.get();
        root = new FailingParent(context);
        activity.get().setContentView(root);
        host = new RecordingHost(context);
        FrameLayout.LayoutParams hostParams = new FrameLayout.LayoutParams(300, 100);
        hostParams.leftMargin = 40;
        hostParams.topMargin = 20;
        root.addView(host, hostParams);
        nativeRoot = new FrameLayout(context);
        host.addView(nativeRoot, new FrameLayout.LayoutParams(300, 100));
        pill = new CapsuleView(context);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(60, 30);
        pillParams.leftMargin = 20;
        pillParams.topMargin = 10;
        nativeRoot.addView(pill, pillParams);
        window = new View(context);
        root.addView(window, new FrameLayout.LayoutParams(500, 1000));
        layout();
        source = new NativeCapsuleSource(host);
        region = new Region();
    }
    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 500, 1000);
    }
    @Test public void activeProviderAnimationCanWakeItsNewOutputEvenWhenTheNativeHostIsHidden(){
        android.widget.ImageView image=new android.widget.ImageView(host.getContext());
        Animated drawable=new Animated();image.setImageDrawable(drawable);pill.addView(image,new FrameLayout.LayoutParams(20,20));layout();
        source=new NativeCapsuleSource(host,true);source.independentHostPresentation=true;host.setVisibility(View.INVISIBLE);
        source.refresh(window,region);assertFalse(source.hasRunningAnimation());
        drawable.start();assertTrue(source.hasRunningAnimation());drawable.stop();assertFalse(source.hasRunningAnimation());
        drawable.start();image.setVisibility(View.INVISIBLE);assertFalse(source.hasRunningAnimation());
    }
    @Test public void replacingAnAnimatedDrawableDoesNotKeepAStalePermanentFrameLoop(){
        android.widget.ImageView image=new android.widget.ImageView(host.getContext());Animated previous=new Animated();
        image.setImageDrawable(previous);pill.addView(image,new FrameLayout.LayoutParams(20,20));layout();source.refresh(window,region);
        previous.start();assertTrue(source.hasRunningAnimation());image.setImageDrawable(new android.graphics.drawable.ColorDrawable(Color.BLUE));
        assertFalse(source.hasRunningAnimation());Animated next=new Animated();next.start();image.setImageDrawable(next);assertTrue(source.hasRunningAnimation());
        pill.removeView(image);assertFalse(source.hasRunningAnimation());
    }
    private static final class Animated extends android.graphics.drawable.ColorDrawable implements android.graphics.drawable.Animatable{
        boolean running;Animated(){super(Color.RED);}public void start(){running=true;}public void stop(){running=false;}public boolean isRunning(){return running;}
    }
    @Test public void independentWindowUsesOnlyTheSourceBandAndKeepsQuantizedShadowSpace() {
        assertEquals(225,NativeCapsuleSource.viewportHeight(new android.graphics.RectF(0,0,1272,157),2772,45));
        assertEquals(225,NativeCapsuleSource.viewportHeight(new android.graphics.RectF(0,0,1272,160),2772,45));
        assertEquals(2772,NativeCapsuleSource.viewportHeight(new android.graphics.RectF(0,0,1272,3000),2772,45));
        assertEquals(2772,NativeCapsuleSource.viewportHeight(new android.graphics.RectF(0,0,1272,Float.MAX_VALUE),2772,45));
        assertEquals(2772,NativeCapsuleSource.viewportHeight(new android.graphics.RectF(0,0,1272,Float.NaN),2772,45));
        assertEquals(2772,NativeCapsuleSource.viewportHeight(new android.graphics.RectF(),2772,45));
        var independent=new NativeCapsuleSource(host,true);
        int height=independent.viewportHeight(window,1000);
        assertTrue(height>=120);assertTrue(height<200);
        window.layout(0,0,500,height);independent.refresh(window,region);
        assertTrue(region.contains(65,35)); // reducing surface height preserves native touch coordinates
    }
    @After public void tearDown() { activity.pause().stop().destroy(); }

    @Test public void nativeDotGeometryUsesTheProviderPairInsteadOfTheDisplayAvoidanceRectangle(){
        nativeRoot.removeView(pill);host.removeView(nativeRoot);
        var container=new ProfileContainer(host.getContext());host.addView(container,new FrameLayout.LayoutParams(100,100));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(60,30);params.leftMargin=20;params.topMargin=10;container.addView(pill,params);layout();
        source.refresh(window,region);var profile=source.profile(window,new android.graphics.RectF(0,0,500,141));
        assertNotNull(profile);assertEquals(10,profile.dot().height(),0);assertEquals(90,profile.dot().centerX(),0);assertEquals(45,profile.dot().centerY(),0);
    }
    @Test public void morphUsesClonedNativeSkinAndIndependentSdkContentWithoutChangingItsParentOrBounds(){
        var background=new android.graphics.drawable.GradientDrawable();background.setColor(Color.BLUE);background.setCornerRadius(15);pill.setBackground(background);
        var payload=new View(host.getContext());payload.setBackgroundColor(Color.RED);pill.addView(payload,new FrameLayout.LayoutParams(-1,-1));layout();
        source.refresh(window,region);Rect original=new Rect(background.getBounds());var originalParent=payload.getParent();
        var profile=new CapsulePresentationGeometry.Profile(new android.graphics.RectF(85,40,95,50),8,9);
        var pose=new CapsuleMotion.Pose(.5,1,0,.3,1,.5,0);var geometry=CapsulePresentationGeometry.resolve(new android.graphics.RectF(region.getBounds()),profile,pose);
        Bitmap bitmap=Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888);assertTrue(source.drawMorph(new Canvas(bitmap),window,geometry,-1f));
        assertEquals(Color.RED,bitmap.getPixel(90,45));assertEquals(Color.BLUE,bitmap.getPixel(78,45));assertEquals(Color.TRANSPARENT,bitmap.getPixel(120,45));
        assertEquals(original,background.getBounds());assertSame(originalParent,payload.getParent());assertEquals(1,payload.getScaleX(),0);
        assertEquals(new Rect(0,0,60,30),new Rect(payload.getLeft(),payload.getTop(),payload.getRight(),payload.getBottom()));
        source.clearMorph();
    }
    public static final class ProfileContainer extends FrameLayout{
        ProfileContainer(Context context){super(context);}public Pair anchor(int height){return new Pair(20,30);}
    }
    public static final class Pair{private final Object first,second;Pair(int a,int b){first=a;second=b;}}
    @Test public void anUncloneableProviderSkinCannotLeaveAPartiallyDrawnMorph(){
        pill.setBackground(new android.graphics.drawable.Drawable(){
            public void draw(Canvas canvas){canvas.drawColor(Color.GREEN);}public void setAlpha(int alpha){}
            public void setColorFilter(android.graphics.ColorFilter filter){}public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
        });
        pill.addView(new View(host.getContext()),new FrameLayout.LayoutParams(-1,-1));layout();source.refresh(window,region);
        var profile=new CapsulePresentationGeometry.Profile(new android.graphics.RectF(85,40,95,50),8,9);
        var geometry=CapsulePresentationGeometry.resolve(new android.graphics.RectF(region.getBounds()),profile,new CapsuleMotion.Pose(.5,1,0,.5,.5,.5,0));
        Bitmap bitmap=Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888);
        assertFalse(source.drawMorph(new Canvas(bitmap),window,geometry,-1f));assertEquals(Color.TRANSPARENT,bitmap.getPixel(90,45));source.clearMorph();
    }

    @Test public void blankSlotKeepsOnlyPreviousCapsuleSwipeBoundsWithoutStalePixels(){
        source.recoverBlank=()->true;source.refresh(window,region);Region original=new Region(region);
        pill.setVisibility(View.INVISIBLE);source.refresh(window,region);
        assertEquals(original,region);assertTrue(source.hasContent());
        MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,65,35,0);
        assertTrue(source.dispatchTouchEvent(down));source.cancelTouch();down.recycle();
        Bitmap bitmap=Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888);source.draw(new Canvas(bitmap));
        assertEquals(Color.TRANSPARENT,bitmap.getPixel(65,35));
        source.recoverBlank=()->false;source.refresh(window,region);assertTrue(region.isEmpty());assertFalse(source.hasContent());
    }
    @Test public void blankRecoveryDoesNotSurviveLayoutChangeOrRelease(){
        source.recoverBlank=()->true;source.refresh(window,region);pill.setVisibility(View.INVISIBLE);
        window.layout(0,0,600,1000);source.refresh(window,region);assertTrue(region.isEmpty());
        pill.setVisibility(View.VISIBLE);source.refresh(window,region);source.clearRecovery();
        pill.setVisibility(View.INVISIBLE);source.refresh(window,region);assertTrue(region.isEmpty());
    }
    @Test public void blankEntryKeepsSwipeBoundsAcrossNativeShrinkAndEmptyTransition(){
        boolean[] blank={false};source.recoverBlank=()->blank[0];source.retainRecovery=()->true;
        source.refresh(window,region);Region original=new Region(region);
        pill.setScaleX(.2f);source.refresh(window,region);
        pill.setVisibility(View.INVISIBLE);source.refresh(window,region);
        assertTrue(region.isEmpty());assertFalse(source.hasContent());
        blank[0]=true;source.refresh(window,region);
        assertEquals(original,region);assertTrue(source.hasContent());
        source.retainRecovery=()->false;source.recoverBlank=()->false;source.refresh(window,region);
        source.recoverBlank=()->true;source.refresh(window,region);assertTrue(region.isEmpty());
    }

    @Test public void liveDrawRetainsOriginalParentsAndOriginalPixelCoordinates() {
        source.refresh(window, region);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertSame(root, host.getParent());
        assertSame(host, nativeRoot.getParent());
        assertSame(nativeRoot, pill.getParent());
        assertEquals(Color.GREEN, bitmap.getPixel(65, 35));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(5, 5));
    }
    @Test public void independentHostVisibilityDoesNotOverrideSdkContentLifecycle() {
        source=new NativeCapsuleSource(host,true);source.independentHostPresentation=true;
        host.setAlpha(0);host.setVisibility(View.INVISIBLE);host.setScaleX(.2f);host.setScaleY(.2f);
        source.refresh(window,region);Bitmap bitmap=Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));assertEquals(Color.GREEN,bitmap.getPixel(65,35));assertTrue(region.contains(65,35));
        nativeRoot.setVisibility(View.INVISIBLE);source.refresh(window,region);
        bitmap.eraseColor(Color.TRANSPARENT);source.draw(new Canvas(bitmap));
        assertEquals(Color.TRANSPARENT,bitmap.getPixel(65,35));assertTrue(region.isEmpty());
        assertSame(root,host.getParent());assertSame(host,nativeRoot.getParent());assertSame(nativeRoot,pill.getParent());
    }
    @Test public void nativeRootAlphaAndTranslationAreNotResetByMirroring() {
        nativeRoot.setAlpha(0.5f);
        nativeRoot.setTranslationX(15);
        source.refresh(window, region);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertEquals(0.5f, nativeRoot.getAlpha(), 0.001f);
        assertEquals(15, nativeRoot.getTranslationX(), 0.001f);
        assertTrue(Color.alpha(bitmap.getPixel(80, 35)) >= 126);
        assertTrue(Color.alpha(bitmap.getPixel(80, 35)) <= 129);
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(65, 35));
    }
    @Test public void independentPlacementIgnoresBarAncestorMotionButKeepsCapsuleMotion() {
        View overlay = new View(activity.get());
        overlay.layout(0, 0, 500, 1000);
        source = new NativeCapsuleSource(host, true);
        root.setTranslationY(-60);
        root.setAlpha(0f);
        root.setClipBounds(new Rect());
        nativeRoot.setTranslationX(15);
        nativeRoot.setAlpha(0.5f);
        source.refresh(overlay, region);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertTrue(region.contains(80, 35));
        assertTrue(Color.alpha(bitmap.getPixel(80, 35)) >= 126);
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(65, 35));
        assertEquals(-60f, root.getTranslationY(), 0.001f);
        assertEquals(0f, root.getAlpha(), 0.001f);
        assertEquals(15f, nativeRoot.getTranslationX(), 0.001f);
    }
    @Test public void onlyNativeCapsuleBoundsBecomeTouchable() {
        source.refresh(window, region);
        assertTrue(region.contains(65, 35));
        assertFalse(region.contains(10, 35));
        assertFalse(region.contains(125, 35));
        assertFalse(region.contains(65, 70));
    }
    @Test public void pendingContentLayoutRunsOriginalCallbacksWithoutMovingHost() {
        final int[] layouts = {0};
        pill.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> layouts[0]++);
        pill.getLayoutParams().width = 90;
        pill.requestLayout();
        source.refresh(window, region);
        assertEquals(90, pill.getWidth());
        assertEquals(1, layouts[0]);
        assertEquals(40, host.getLeft());
        assertEquals(300, host.getWidth());
    }
    @Test public void touchMappingKeepsRawCoordinatesAndDoesNotMutateInput() {
        source.refresh(window, region);
        MotionEvent down = MotionEvent.obtain(100, 100, MotionEvent.ACTION_DOWN, 80, 45, 0);
        assertTrue(source.dispatchTouchEvent(down));
        assertEquals(40, host.lastX, 0.001f);
        assertEquals(25, host.lastY, 0.001f);
        assertEquals(80, host.lastRawX, 0.001f);
        assertEquals(45, host.lastRawY, 0.001f);
        assertEquals(80, down.getX(), 0.001f);
        down.recycle();
        source.cancelTouch();
        assertEquals(MotionEvent.ACTION_CANCEL, host.lastAction);
        assertEquals(100, host.lastDownTime);
    }
    @Test public void invisibleNativeContentDoesNotProduceAStaleMirrorOrTouchArea() {
        source.refresh(window, region);
        assertTrue(source.hasContent());
        pill.setVisibility(View.INVISIBLE);
        source.refresh(window, region);
        assertFalse(source.hasContent());
        assertTrue(region.isEmpty());
    }
    @Test public void completedShrinkCannotDrawRestoredCapsuleFromAnEarlierPreparedFrame() {
        source = new NativeCapsuleSource(host, true);
        pill.layout(20, 10, 30, 40); // Last visible shrink frame: narrow capsule.
        source.refresh(window, region);
        assertTrue(source.hasContent());
        // An animator callback can finish after prepare(), before this window's draw traversal.
        host.setAlpha(0f);
        host.setVisibility(View.INVISIBLE);
        pill.layout(20, 10, 80, 40); // Native presentation cleanup restores the full measured size.
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(65, 35));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(110, 35));
        bitmap.recycle();
    }
    @Test public void drawingUsesCurrentHostAlphaWhenAnimatorRunsAfterFramePreparation() {
        source = new NativeCapsuleSource(host, true);
        source.refresh(window, region);
        host.setAlpha(.25f);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertEquals(64, Color.alpha(bitmap.getPixel(65, 35)), 1);
        bitmap.recycle();
    }
    @Test public void animatedScaleChangesTouchBoundsWithTheNativeView() {
        pill.setPivotX(0);
        pill.setPivotY(0);
        pill.setScaleX(0.5f);
        source.refresh(window, region);
        assertTrue(region.contains(65, 35));
        assertFalse(region.contains(95, 35));
    }
    @Test public void handoffIsAcceptedOnlyInsideNativeDispatchAndContinuesBeyondCapsule() {
        source.refresh(window, region);
        source.markNativeHandoff();
        assertFalse(source.hasHandedOffTouch());
        host.duringDispatch = source::markNativeHandoff;
        MotionEvent down = MotionEvent.obtain(100, 100, MotionEvent.ACTION_DOWN, 80, 45, 0);
        source.dispatchTouchEvent(down);
        down.recycle();
        assertTrue(source.hasHandedOffTouch());
        host.duringDispatch = null;
        // The shade has opened and the native capsule can disappear mid-drag.
        pill.setVisibility(View.INVISIBLE);
        source.refresh(window, region);
        assertFalse(source.hasContent());
        MotionEvent move = MotionEvent.obtain(100, 110, MotionEvent.ACTION_MOVE, 200, 300, 0);
        assertTrue(source.dispatchTouchEvent(move));
        move.recycle();
        assertEquals(MotionEvent.ACTION_MOVE, host.lastAction);
        assertEquals(300, host.lastRawY, 0.001f);
        MotionEvent up = MotionEvent.obtain(100, 120, MotionEvent.ACTION_UP, 200, 300, 0);
        source.dispatchTouchEvent(up);
        up.recycle();
        assertEquals(MotionEvent.ACTION_UP, host.lastAction);
        assertFalse(source.hasHandedOffTouch());
        source.cancelTouch();
        assertEquals(MotionEvent.ACTION_UP, host.lastAction);
    }
    @Test public void hardCancellationEndsHandedOffStream() {
        source.refresh(window, region);
        host.duringDispatch = source::markNativeHandoff;
        MotionEvent down = MotionEvent.obtain(100, 100, MotionEvent.ACTION_DOWN, 80, 45, 0);
        source.dispatchTouchEvent(down);
        down.recycle();
        host.duringDispatch = null;
        source.cancelTouch();
        assertEquals(MotionEvent.ACTION_CANCEL, host.lastAction);
        assertFalse(source.hasHandedOffTouch());
    }
    @Test public void ancestorExplicitClipLimitsPixelsAndInputTogether() {
        root.setClipBounds(new Rect(0, 0, 70, 1000));
        source.refresh(window, region);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertEquals(Color.GREEN, bitmap.getPixel(65, 35));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(75, 35));
        assertTrue(region.contains(65, 35));
        assertFalse(region.contains(75, 35));
    }
    @Test public void ancestorPaddingClipLimitsPixelsAndInputTogether() {
        root.setPadding(0, 40, 0, 0);
        root.setClipToPadding(true);
        source.refresh(window, region);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(65, 35));
        assertEquals(Color.GREEN, bitmap.getPixel(65, 45));
        assertFalse(region.contains(65, 35));
        assertTrue(region.contains(65, 45));
    }
    @Test public void allowedNativeOverflowIsNotClippedToHostBounds() {
        host.getLayoutParams().width = 200;
        nativeRoot.getLayoutParams().width = 200;
        layout();
        root.setClipChildren(false);
        host.setClipChildren(false);
        nativeRoot.setClipChildren(false);
        pill.setTranslationX(180);
        source.refresh(window, region);
        Bitmap bitmap = Bitmap.createBitmap(500, 1000, Bitmap.Config.ARGB_8888);
        source.draw(new Canvas(bitmap));
        assertEquals(Color.GREEN, bitmap.getPixel(255, 35));
        assertTrue(region.contains(255, 35));
    }

    @Test public void coveredCapsuleContentDoesNotRepeatDescendantHitGeometryWork() {
        CapsuleView nested = new CapsuleView(activity.get());
        pill.addView(nested, new FrameLayout.LayoutParams(20, 20));
        layout();
        source.refresh(window, region);
        assertTrue(region.contains(65, 35));
        assertTrue(region.contains(115, 55));
        pill.childQueries = 0;
        source.refresh(window, region);
        assertEquals("The capsule already covers every descendant's possible hit region", 0, pill.childQueries);
    }


    @Test public void capsuleDescendantsOutsideItsOwnBoundsStillContributeWhenUnclipped() {
        root.setClipChildren(false);
        host.setClipChildren(false);
        nativeRoot.setClipChildren(false);
        pill.setClipChildren(false);
        CapsuleView nested = new CapsuleView(activity.get());
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(20, 20);
        position.leftMargin = 100;
        pill.addView(nested, position);
        layout();
        source.refresh(window, region);
        assertTrue(region.contains(65, 35));
        assertTrue(region.contains(165, 35));
    }

    @Test public void unrelatedStatusBarChangesDoNotDirtyTheCapsuleButVisibleDescendantUpdatesDo() {
        source.refresh(window,region);
        source.draw(new Canvas(Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888)));
        assertFalse(source.hasPendingPixels());
        View sibling=new View(activity.get());root.addView(sibling,new FrameLayout.LayoutParams(20,20));sibling.invalidate();
        assertFalse(source.hasPendingPixels());
        pill.invalidate();assertTrue(source.hasPendingPixels());
        source.draw(new Canvas(Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888)));
        assertFalse(source.hasPendingPixels());
        View hidden=new View(activity.get());hidden.setVisibility(View.GONE);
        nativeRoot.addView(hidden,new FrameLayout.LayoutParams(20,20));source.refresh(window,region);
        source.draw(new Canvas(Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888)));
        hidden.invalidate();assertFalse(source.hasPendingPixels());
    }

    // Robolectric does not provide a compositor/AttachedSurfaceControl for these system windows.
    // Replace only that external boundary; source drawing, window state and gestures stay real.

    private static final class FailingParent extends FrameLayout {
        boolean failInvalidation;
        FailingParent(Context context) { super(context); }
        @Override public void invalidate() {
            if (failInvalidation) throw new IllegalStateException("parent invalidation failed");
            super.invalidate();
        }
    }
    public static class CapsuleView extends FrameLayout {
        final Paint paint = new Paint();
        int childQueries;
        CapsuleView(Context context) { super(context); setWillNotDraw(false); paint.setColor(Color.GREEN); }
        @Override public int getChildCount() { childQueries++; return super.getChildCount(); }
        @Override protected void onDraw(Canvas canvas) { canvas.drawRect(0, 0, getWidth(), getHeight(), paint); }
    }
    private static final class RecordingHost extends FrameLayout {
        float lastX, lastY, lastRawX, lastRawY;
        int lastAction;
        long lastDownTime;
        Runnable duringDispatch;
        Runnable duringDraw;
        RecordingHost(Context context) { super(context); }
        @Override protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (duringDraw != null) duringDraw.run();
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            lastX = event.getX(); lastY = event.getY();
            lastRawX = event.getRawX(); lastRawY = event.getRawY();
            lastAction = event.getActionMasked(); lastDownTime = event.getDownTime();
            if (duringDispatch != null) duringDispatch.run();
            return true;
        }
    }
}
