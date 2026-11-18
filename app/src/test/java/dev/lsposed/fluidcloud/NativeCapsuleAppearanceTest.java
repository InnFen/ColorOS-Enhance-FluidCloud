package dev.lsposed.fluidcloud;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativeCapsuleAppearanceTest {
    @Test public void disablingRestoresOwnedOffTreeCopiesWithoutWaitingForAnotherNativeUpdate()throws Exception{
        var api=contract(false);var main=new Capsule(context());var offTree=new Capsule(context());
        main.b(0);offTree.b(0);api.stroke(main,true);api.stroke(offTree,true);
        var mainMoon=new Container(context());var copyMoon=new Container(context());
        api.refresh(mainMoon,false,true);api.refresh(copyMoon,false,true);
        api.restoreDisabled(false,false);
        assertEquals(Color.RED,edge(main.f));assertEquals(Color.RED,edge(offTree.f));
        assertEquals(View.VISIBLE,mainMoon.left.getVisibility());assertEquals(View.VISIBLE,copyMoon.left.getVisibility());
    }
    @Test public void hidingAndRestoringStrokePreservesCurrentNativeBackgroundDecisionAndSdkColor()throws Exception{
        for(boolean old:new boolean[]{false,true}){
            var api=contract(old);var view=old?new OldCapsule(context()):new Capsule(context());
            GradientDrawable drawable=old?((OldCapsule)view).q:((Capsule)view).f;
            api.dark.invoke(view,0f);assertEquals(Color.RED,edge(drawable));
            api.stroke(view,true);assertEquals(Color.WHITE,edge(drawable));
            if(old)((OldCapsule)view).p=Color.BLUE;else((Capsule)view).e=Color.BLUE;
            var callback=new Callback();callback.b=callback.c=view;
            api.stroke(api.colorOwner(callback),true);assertEquals(Color.WHITE,edge(drawable));
            api.stroke(view,false);assertEquals(Color.BLUE,edge(drawable));
            api.dark.invoke(view,1f);api.stroke(view,true);api.stroke(view,false);
            assertEquals(Color.WHITE,edge(drawable)); // A light-background native border stays absent.
        }
    }
    @Test public void indicatorRefreshHidesWithoutChangingSwipeLayoutAndRestoresLatestNativeRequest()throws Exception{
        var api=contract(false);var view=new Container(context());
        api.refresh(view,false,true);assertEquals(View.GONE,view.left.getVisibility());
        api.restore();assertEquals(View.VISIBLE,view.left.getVisibility());
        api.refresh(view,false,true);
        view.setMoonViewShown(api.moonRequest(view,false,true));api.restore();
        assertEquals(View.GONE,view.left.getVisibility());assertEquals(View.GONE,view.right.getVisibility());
        assertEquals(2,view.getChildCount());
    }
    @Test public void anEmptyNativeStateNeverRestoresStaleArrows()throws Exception{
        for(boolean old:new boolean[]{false,true}){
            var api=contract(old);var view=new Container(context());api.refresh(view,false,true);
            view.m=State.d;view.x=State.HIDE;api.afterState(view,true);api.restore();
            assertEquals(View.GONE,view.left.getVisibility());
        }
    }
    private Context context(){return RuntimeEnvironment.getApplication();}
    private static GradientDrawable foreground(){var g=new GradientDrawable();g.setColor(Color.WHITE);g.setBounds(0,0,100,60);return g;}
    private static int edge(GradientDrawable drawable){var image=Bitmap.createBitmap(100,60,Bitmap.Config.ARGB_8888);drawable.draw(new Canvas(image));return image.getPixel(50,1);}
    private NativeCapsuleAppearance contract(boolean old)throws Exception{
        var types=Map.<String,Class<?>>of(NativeCapsuleAppearance.VIEWS+"CapsuleView",old?OldCapsule.class:Capsule.class,
                NativeCapsuleAppearance.VIEWS+"CapsuleContainer",Container.class,
                old?"w4.d":"com.oplus.systemui.plugins.seedling.capsule.data.repo.d",Callback.class,
                old?"y4.f":"com.oplus.systemui.plugins.seedling.capsule.ui.model.f",Model.class);
        return new NativeCapsuleAppearance(name->{Class<?> found=types.get(name);if(found==null)throw new ClassNotFoundException(name);return found;});
    }
    public static class Capsule extends FrameLayout{
        public GradientDrawable f=foreground();public float d;public int e=Color.RED;
        Capsule(Context c){super(c);}
        public void b(float v){d=v<=.13?0:1;f.setStroke(d==0?4:0,e);}
        @Override public void onFinishInflate(){super.onFinishInflate();}
    }
    public static class OldCapsule extends FrameLayout{
        public GradientDrawable q=foreground();public float o;public int p=Color.RED;
        OldCapsule(Context c){super(c);}
        public void b(float v){o=v<=.13?0:1;q.setStroke(o==0?4:0,p);}
        @Override public void onFinishInflate(){super.onFinishInflate();}
    }
    enum State{b,d,HIDE}
    public static class Model{}
    public static class Container extends FrameLayout{
        public State m=State.b,x=State.b;final ImageView left,right;
        Container(Context c){super(c);left=new ImageView(c);right=new ImageView(c);addView(left);addView(right);}
        public ImageView getLeftMoon(){return left;}public ImageView getRightMoon(){return right;}
        public void setMoonViewShown(boolean shown){left.setVisibility(shown?VISIBLE:GONE);right.setVisibility(shown?VISIBLE:GONE);}
        public void h(Model state,boolean animate){}public void i(Model state,boolean animate){}
    }
    public static class Callback{public Object b,c;public void b(Bundle values){}public void m(Bundle values){}}
}
