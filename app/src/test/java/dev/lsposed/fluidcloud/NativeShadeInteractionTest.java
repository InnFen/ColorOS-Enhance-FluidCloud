package dev.lsposed.fluidcloud;
import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class NativeShadeInteractionTest {
    @Test public void inspectedModernAndLegacyContractsRetainOnlyLiveBlankSlots()throws Exception{
        for(boolean older:new boolean[]{false,true}){
            var activity=Robolectric.buildActivity(Activity.class).setup().visible();
            try{
                var container=new Container(activity.get());activity.get().setContentView(container);
                var nativeInput=new NativeShadeInteraction(lookup(older));assertEquals(older,nativeInput.legacy);
                assertTrue(nativeInput.hasRecoverableItems(container));assertFalse(nativeInput.blankRecoverable(container));container.m=Content.d;container.x=Content.HIDE;
                assertTrue(nativeInput.blankRecoverable(container));container.items=0;assertFalse(nativeInput.blankRecoverable(container));
                assertFalse(nativeInput.hasRecoverableItems(container));
                container.items=1;container.mini=true;assertFalse(nativeInput.blankRecoverable(container));assertFalse(nativeInput.hasRecoverableItems(container));
                container.mini=false;container.setVisibility(android.view.View.GONE);assertTrue(nativeInput.blankRecoverable(container));
                container.m=Content.NORMAL;container.x=Content.NORMAL;assertFalse(nativeInput.hasRecoverableItems(container));
            }finally{activity.pause().stop().destroy();}
        }
    }
    @Test public void clickPermissionIsScopedToUpAndRestoresActualQsState()throws Exception{
        for(boolean older:new boolean[]{false,true}){
            var nativeInput=new NativeShadeInteraction(lookup(older));var root=new Root(Robolectric.buildActivity(Activity.class).setup().get());
            State.j=new State();State.j.c.c=true;
            MotionEvent move=MotionEvent.obtain(1,2,MotionEvent.ACTION_MOVE,1,1,0),up=MotionEvent.obtain(1,3,MotionEvent.ACTION_UP,1,1,0);
            assertNull(nativeInput.allowClick(root,move,()->true));assertTrue(State.j.c.c);
            var gate=nativeInput.allowClick(root,up,()->true);assertFalse(State.j.c.c);
            try{throw new IllegalStateException("native handler failed");}catch(IllegalStateException expected){}finally{gate.close();}
            assertTrue(State.j.c.c);nativeInput.allowClick(root,up,()->false).close();assertFalse(State.j.c.c);
            move.recycle();up.recycle();
        }
    }
    @Test public void deferredClickCannotFollowAReplacementKeyOrMiniCapsule()throws Exception{
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var container=new Container(activity.get());activity.get().setContentView(container);var nativeInput=new NativeShadeInteraction(lookup(false));
            var target=nativeInput.target(container,container.n);assertNotNull(target);assertTrue(nativeInput.valid(container,target));
            assertTrue(nativeInput.isClick(new Click()));assertFalse(nativeInput.isClick(new Action()));
            container.l="replaced";assertFalse(nativeInput.valid(container,target));container.l="media";container.mini=true;assertFalse(nativeInput.valid(container,target));
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void contentGenerationIsStableForMetadataButNotAChangedItemOrModel()throws Exception{
        for(boolean older:new boolean[]{false,true}){
            var activity=Robolectric.buildActivity(Activity.class).setup().visible();
            try{
                var container=new Container(activity.get());activity.get().setContentView(container);var input=new NativeShadeInteraction(lookup(older));
                Object first=input.contentIdentity(container);assertNotNull(first);assertEquals(first,input.contentIdentity(container));
                container.l="next-item";container.w="next-item";assertNotEquals(first,input.contentIdentity(container));
                container.l="media";container.w="media";container.n=new Model();container.y=container.n;assertNotEquals(first,input.contentIdentity(container));
                container.m=Content.d;container.x=Content.HIDE;assertNull(input.contentIdentity(container));
                container.m=Content.NORMAL;container.x=Content.NORMAL;container.mini=true;assertNull(input.contentIdentity(container));
            }finally{activity.pause().stop().destroy();}
        }
    }
    @Test public void retainedPanelUsesNativePluginCallbacksAndRestoresTheLatestActualQsState()throws Exception{
        for(boolean older:new boolean[]{false,true}){
            var input=new NativeShadeInteraction(lookup(older));State.j=new State();State.j.c.c=true;
            input.presentQs(true,()->true);assertFalse(State.j.c.c);assertEquals(1,State.j.g.calls);
            input.presentQs(true,()->true);assertEquals(1,State.j.g.calls);
            assertFalse(input.filterQs(State.j.g,true,true));assertTrue(input.filterQs(new QsListener(new State()),true,true));
            input.presentQs(false,()->true);assertTrue(State.j.c.c);assertEquals(2,State.j.g.calls);
            assertTrue(input.filterQs(State.j.g,true,false));input.presentQs(false,()->false);assertEquals(2,State.j.g.calls);
            input.presentQs(true,()->true);input.presentQs(false,()->false);assertFalse(State.j.c.c);
        }
    }
    @Test public void pluginReplacementRestoresTheOldListenerAndBindsOnlyTheNewOne()throws Exception{
        var input=new NativeShadeInteraction(lookup(false));State.j=new State();State previous=State.j;
        input.presentQs(true,()->true);State.j=new State();State.j.c.c=true;input.presentQs(true,()->true);
        assertTrue(previous.c.c);assertFalse(State.j.c.c);assertEquals(2,previous.g.calls);assertEquals(1,State.j.g.calls);
        input.presentQs(false,()->true);assertTrue(State.j.c.c);
    }
    private static NativeShadeInteraction.Lookup lookup(boolean old){
        Map<String,Class<?>> types=Map.ofEntries(
                Map.entry(NativeShadeInteraction.VIEWS+"CapsuleContainerRoot",Root.class),Map.entry(NativeShadeInteraction.VIEWS+"CapsuleContainer",Container.class),
                Map.entry(old?"a5.q":"com.oplus.systemui.plugins.seedling.capsule.ui.viewmodel.u",Model.class),
                Map.entry(old?"y4.a":"com.oplus.systemui.plugins.seedling.capsule.ui.model.a",Click.class),
                Map.entry(old?"com.oplus.systemui.plugins.seedling.state.n":"com.oplus.systemui.plugins.seedling.state.q",State.class),
                Map.entry(old?"g4.i":"com.oplus.systemui.plugins.baseservice.g",Cache.class),
                Map.entry(old?"v4.e":"com.oplus.systemui.plugins.seedling.capsule.data.model.ContentState",Content.class));
        return name->{Class<?> type=types.get(name);if(type==null)throw new ClassNotFoundException(name);return type;};
    }
    public static class Action{} public static class Click extends Action{}
    public static class Model{public void k(Action action){}public void j(Action action){}}
    public enum Content{NORMAL,MIN,d,HIDE}
    public static class Cache{public boolean c;}
    public static class State{public static volatile State j=new State();public final Cache c=new Cache();public final QsListener g=new QsListener(this);}
    public static class QsListener{final State state;int calls;QsListener(State state){this.state=state;}public void onQSExpandedChanged(boolean expanded){calls++;state.c.c=expanded;}}
    public static class Root extends FrameLayout{public Root(Context context){super(context);}@Override public boolean onTouchEvent(MotionEvent event){return true;}}
    public static class Container extends FrameLayout{
        public Model n=new Model(),y=n;public String l="media",w="media";public Content m=Content.NORMAL,x=Content.NORMAL;int items=1;boolean mini;
        public Container(Context context){super(context);}public int getCapsuleViewNum(){return items;}public boolean j(){return mini;}public boolean k(){return mini;}
    }
}
