package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayDeque;
import java.util.Map;
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

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PresentationWindowTest {
    ActivityController<Activity> activity;FrameLayout root;RecordingHost host;FrameLayout body;CapsuleView pill;
    PresentationWindow window;View surface;CloudPresentationCoordinator.Ticket ticket=new CloudPresentationCoordinator.Ticket(1,0);
    boolean allowed=true;int failures;
    @Before public void setup()throws Exception{
        activity=Robolectric.buildActivity(Activity.class).setup().visible();Context context=activity.get();
        root=new FrameLayout(context);activity.get().setContentView(root);
        host=new RecordingHost(context);FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(300,100);hp.leftMargin=40;hp.topMargin=20;root.addView(host,hp);
        body=new FrameLayout(context);host.addView(body,new FrameLayout.LayoutParams(300,100));
        pill=new CapsuleView(context);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(60,30);pp.leftMargin=20;pp.topMargin=10;body.addView(pill,pp);
        root.measure(View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));root.layout(0,0,500,1000);
        window=new PresentationWindow(host,()->new RectF(80,30,100,60),()->allowed,value->ticket.equals(value),()->{},()->{},error->failures++);
        window.frameSync=(nativeHost,shade,mirror,nativeChange,mirrorChange,committed)->{nativeChange.run();mirrorChange.run();committed.run();};
        surface=ReflectionHelpers.getField(window,"view");surface.measure(View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));surface.layout(0,0,500,1000);
    }
    @After public void cleanup(){window.dispose();activity.pause().stop().destroy();}
    void stage(double fraction){window.prepare();window.render(new CloudPresentationCoordinator.Frame(ticket,"media",CloudPresentationCoordinator.Owner.MODULE,fraction));}
    void show(){stage(1);window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->{});}
    Bitmap draw(){Bitmap bitmap=Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888);surface.draw(new Canvas(bitmap));return bitmap;}
    float nativeAlpha()throws Exception{return (float)Reflect.call(Reflect.method(View.class,"getTransitionAlpha",float.class),host);}
    boolean touch(int action,float x,float y){MotionEvent event=MotionEvent.obtain(100,110,action,x,y,0);try{return surface.onTouchEvent(event);}finally{event.recycle();}}
    @Test public void firstCommittedModuleFrameAlreadyHasPixelsAndOnlyThenSuppressesNative()throws Exception{
        Pending frames=new Pending();window.frameSync=frames;stage(1);window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->{});
        assertEquals(1,nativeAlpha(),0);assertEquals(Color.TRANSPARENT,draw().getPixel(65,35));
        frames.commit();assertEquals(0,nativeAlpha(),0);assertEquals(Color.GREEN,draw().getPixel(65,35));
    }
    @Test public void returningNativeClearsModuleInTheSameCommittedFrame()throws Exception{
        show();Pending frames=new Pending();window.frameSync=frames;
        window.request(CloudPresentationCoordinator.Owner.NATIVE,ticket,()->{});
        assertEquals(Color.GREEN,draw().getPixel(65,35));assertEquals(0,nativeAlpha(),0);
        frames.commit();assertEquals(Color.TRANSPARENT,draw().getPixel(65,35));assertEquals(1,nativeAlpha(),0);
    }
    @Test public void nativeWrapperVisibilityAlphaAndScaleDoNotReanimateOwnedPixels(){
        show();host.setVisibility(View.INVISIBLE);host.setAlpha(0);host.setScaleX(.2f);host.setScaleY(.2f);window.prepare();
        assertEquals(Color.GREEN,draw().getPixel(65,35));assertTrue(touch(MotionEvent.ACTION_DOWN,80,45));touch(MotionEvent.ACTION_CANCEL,80,45);
        body.setVisibility(View.INVISIBLE);window.prepare();assertEquals(Color.TRANSPARENT,draw().getPixel(65,35));
    }
    @Test public void pointerCoordinatesUseTheSameTransformAsShrinkingPixels(){
        stage(.5);window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->{});
        var geometry=CapsulePresentationGeometry.resolve(new RectF(60,30,120,60),new RectF(80,30,100,60),.5);
        float[] point={80,45};geometry.forward().mapPoints(point);assertTrue(touch(MotionEvent.ACTION_DOWN,point[0],point[1]));
        assertEquals(40,host.x,.01);assertEquals(25,host.y,.01);touch(MotionEvent.ACTION_CANCEL,point[0],point[1]);
    }
    @Test public void changingOnlyTheMorphInputRegionDoesNotRequestAnotherFullLayout(){
        show();surface.measure(View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY));surface.layout(0,0,500,1000);
        assertFalse(surface.isLayoutRequested());stage(.6);assertFalse(surface.isLayoutRequested());
    }
    @Test public void hiddenOutputKeepsNativeSuppressedAndHasNoTouchablePixels()throws Exception{
        show();window.render(new CloudPresentationCoordinator.Frame(ticket,"media",CloudPresentationCoordinator.Owner.HIDDEN,0));
        window.request(CloudPresentationCoordinator.Owner.HIDDEN,ticket,()->{});
        assertEquals(0,nativeAlpha(),0);assertEquals(Color.TRANSPARENT,draw().getPixel(65,35));
        assertTrue(((Region)ReflectionHelpers.getField(window,"touch")).isEmpty());assertFalse(touch(MotionEvent.ACTION_DOWN,80,45));
    }
    @Test public void rapidReversalSerializesTransactionsAndCannotRevealNativeBetweenModuleFrames()throws Exception{
        show();Pending frames=new Pending();window.frameSync=frames;
        stage(0);
        window.request(CloudPresentationCoordinator.Owner.HIDDEN,ticket,()->{});
        stage(.4);
        window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->{});
        assertTrue(frames.callbacks.isEmpty());assertEquals(0,nativeAlpha(),0);
        assertNotEquals(Color.TRANSPARENT,draw().getPixel(90,45));
        stage(1);assertEquals(Color.GREEN,draw().getPixel(65,35));
    }
    @Test public void reversalBeforeNativeReturnCommitKeepsTheLastValidModuleBuffer()throws Exception{
        show();Pending frames=new Pending();window.frameSync=frames;
        window.request(CloudPresentationCoordinator.Owner.NATIVE,ticket,()->fail("obsolete return"));
        ticket=new CloudPresentationCoordinator.Ticket(1,1);stage(1);
        window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->{});
        assertEquals(Color.GREEN,draw().getPixel(65,35));frames.commit();
        assertEquals(Color.GREEN,draw().getPixel(65,35));assertEquals(0,nativeAlpha(),0);assertTrue(frames.callbacks.isEmpty());
    }
    @Test public void protectionReleaseMakesPendingCallbacksHarmlessAndRestoresNative()throws Exception{
        Pending frames=new Pending();window.frameSync=frames;stage(1);window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->fail("late ready"));
        allowed=false;window.release();frames.commit();assertEquals(1,nativeAlpha(),0);assertEquals(Color.TRANSPARENT,draw().getPixel(65,35));
    }
    @Test public void nativeShadeDragHasOneInputTargetAcrossVisualHandoff(){
        show();host.duringTouch=window.source::markNativeHandoff;assertTrue(touch(MotionEvent.ACTION_DOWN,80,45));host.duringTouch=null;
        window.request(CloudPresentationCoordinator.Owner.NATIVE,ticket,()->{});assertEquals(View.VISIBLE,surface.getVisibility());
        assertTrue(touch(MotionEvent.ACTION_MOVE,100,150));assertEquals(MotionEvent.ACTION_MOVE,host.action);
        assertTrue(touch(MotionEvent.ACTION_UP,100,150));assertEquals(MotionEvent.ACTION_UP,host.action);assertEquals(View.INVISIBLE,surface.getVisibility());
    }
    @Test public void cancelDuringUpDoesNotReenterNativeTouchHandling(){
        show();assertTrue(touch(MotionEvent.ACTION_DOWN,80,45));host.duringTouch=()->{if(host.action==MotionEvent.ACTION_UP)window.cancelTouch();};
        assertTrue(touch(MotionEvent.ACTION_UP,80,45));assertEquals(1,host.maxDepth);assertEquals(MotionEvent.ACTION_UP,host.action);
    }
    @Test public void failedTransferRestoresOutputAndCannotLeaveAnInputWindow()throws Exception{
        window.frameSync=(a,b,c,nativeChange,moduleChange,committed)->{nativeChange.run();throw new IllegalStateException("transaction failed");};
        stage(1);try{window.request(CloudPresentationCoordinator.Owner.MODULE,ticket,()->{});fail();}catch(IllegalStateException expected){}
        window.dispose();assertEquals(1,nativeAlpha(),0);assertFalse(surface.isAttachedToWindow());
    }
    @Test public void staleContentTicketDoesNotPaintOrAcceptANewGesture(){
        show();ticket=new CloudPresentationCoordinator.Ticket(2,0);
        assertEquals(Color.TRANSPARENT,draw().getPixel(65,35));assertFalse(touch(MotionEvent.ACTION_DOWN,80,45));
        stage(1);assertEquals(Color.GREEN,draw().getPixel(65,35));
    }
    @Test public void coordinatorAndWindowKeepEveryIntermediateBufferThroughReversalsAndContentUpdates()throws Exception{
        Pending compositor=new Pending();window.frameSync=compositor;TestClock clock=new TestClock();
        CloudPresentationCoordinator core=new CloudPresentationCoordinator(new CloudPresentationCoordinator.Port(){
            public void prepare(Object value){window.prepare();}
            public void render(CloudPresentationCoordinator.Frame value){ticket=value.ticket();window.render(value);}
            public void request(CloudPresentationCoordinator.Owner owner,CloudPresentationCoordinator.Ticket value,Runnable ready){window.request(owner,value,ready);}
            public void cancelTouch(){window.cancelTouch();}public void release(){window.release();}
            public void failure(Throwable error){throw new AssertionError(error);}
        },clock);
        CloudConfig config=CloudConfig.from(Map.of("enabled",true,"unbind_status_bar",true,"fullscreen_animation",true,"recents_shrink",true));
        Object content="media";
        core.update(new CloudPresentationCoordinator.Input(host,0,content,true,config,false,false,null,false,false,true));
        assertEquals(1,nativeAlpha(),0);compositor.commit();assertEquals(Color.GREEN,draw().getPixel(65,35));
        for(int i=0;i<120;i++){
            if(i%9==0)content="media-"+i;
            boolean hiding=(i/3%2)==0;
            core.update(new CloudPresentationCoordinator.Input(host,0,content,true,config,false,hiding,null,false,false,true));
            clock.tick();
            assertTrue("Module-owned reversal must not queue cross-window transactions",compositor.callbacks.isEmpty());
            assertEquals(0,nativeAlpha(),0);
            var geometry=CapsulePresentationGeometry.resolve(new RectF(60,30,120,60),new RectF(80,30,100,60),core.current().fraction());
            assertNotEquals("No blank intermediate module buffer",Color.TRANSPARENT,draw().getPixel((int)geometry.bounds().centerX(),(int)geometry.bounds().centerY()));
        }
        core.close();assertEquals(1,nativeAlpha(),0);assertEquals(Color.TRANSPARENT,draw().getPixel(90,45));
    }
    @Test public void suppressedShadeFrameIsRecognizedWithoutInterceptingUnrelatedViews(){
        FrameLayout shade=new FrameLayout(host.getContext());FakePluginFrame fake=new FakePluginFrame(host.getContext());shade.addView(fake);
        window.nativeTargets(shade,true);show();
        assertTrue(window.suppressesNativeOutput(fake));assertFalse(window.suppressesNativeOutput(body));
        window.nativeTargets(shade,false);assertFalse(window.suppressesNativeOutput(fake));
    }
    private static final class TestClock implements CloudPresentationCoordinator.Clock{
        long time=1_000_000_000;Runnable pending;
        public long now(){return time;}public void postFrame(Runnable frame){pending=frame;}public void removeFrame(Runnable frame){if(pending==frame)pending=null;}
        void tick(){time+=16_000_000;Runnable frame=pending;pending=null;if(frame!=null)frame.run();}
    }
    private static final class FakePluginFrame extends FrameLayout{FakePluginFrame(Context context){super(context);}}
    private static final class Pending implements FrameSync{
        final ArrayDeque<Runnable> callbacks=new ArrayDeque<>();
        public void transfer(View a,View b,View c,Runnable nativeChange,Runnable moduleChange,Runnable committed){callbacks.add(()->{nativeChange.run();moduleChange.run();committed.run();});}
        void commit(){callbacks.remove().run();}
    }
    public static final class CapsuleView extends FrameLayout{
        final Paint paint=new Paint();CapsuleView(Context context){super(context);setWillNotDraw(false);paint.setColor(Color.GREEN);}
        @Override protected void onDraw(Canvas canvas){canvas.drawRect(0,0,getWidth(),getHeight(),paint);}
    }
    private static final class RecordingHost extends FrameLayout{
        int action,depth,maxDepth;float x,y;Runnable duringTouch;RecordingHost(Context context){super(context);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){depth++;maxDepth=Math.max(maxDepth,depth);action=event.getActionMasked();x=event.getX();y=event.getY();
            try{if(duringTouch!=null)duringTouch.run();return true;}finally{depth--;}}
    }
}
