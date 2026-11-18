package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class NativeCardDimTest {
    @Test public void firstWindowAddBeforeAttachmentInitializesBackdropOnThatFirstTraversal()throws Exception{
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new Root(activity.get());var card=new Card(activity.get());root.cards.addView(card,new FrameLayout.LayoutParams(1000,500));
            var dim=contract(false);var owner=new Window(root);dim.added(owner,true,60);
            assertEquals(2,root.getChildCount()); // WindowManager.addView has not attached it yet.
            activity.get().setContentView(root);layout(root);root.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(3,root.getChildCount());assertTrue(root.getChildAt(0) instanceof CardBackdropView);
            assertEquals(.6f,root.getChildAt(0).getAlpha(),.0001f);
            dim.removed(owner);
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void backdropSamplesNativeCardFramesAndNeverChangesWindowOrContentOwnership()throws Exception{
        for(boolean old:new boolean[]{false,true}){
            var activity=Robolectric.buildActivity(Activity.class).setup().visible();
            try{
                var root=new Root(activity.get());activity.get().setContentView(root);layout(root);
                var original=root.getLayoutParams();var card=new Card(activity.get());root.cards.addView(card);card.layout(0,0,1000,500);
                var dim=contract(old);var owner=new Window(root);dim.added(owner,true,60);
                var plane=(CardBackdropView)root.getChildAt(0);assertEquals(.6f,plane.getAlpha(),.0001f);
                card.setOutlineProvider(new Provider(new Rect(300,100,700,400)));card.setScaleX(.5f);
                root.getViewTreeObserver().dispatchOnPreDraw();
                assertEquals(CardDimProgress.alpha(200,1000,90,1,60),plane.getAlpha(),.0001f);
                assertFalse(plane.isClickable());assertFalse(plane.isFocusable());assertSame(root.cards,card.getParent());assertSame(original,root.getLayoutParams());
                dim.refresh(false,60);assertEquals(2,root.getChildCount());
                dim.added(owner,true,60);assertEquals(3,root.getChildCount());
                dim.removed(owner);assertEquals(2,root.getChildCount());
            }finally{activity.pause().stop().destroy();}
        }
    }
    @Test public void outgoingInstantCardContinuesFadingAfterNativeCurrentViewAlreadyChanges()throws Exception{
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new Root(activity.get());activity.get().setContentView(root);layout(root);
            var expanded=new FrameLayout(activity.get());root.instant.addView(expanded);expanded.layout(0,0,800,300);
            root.instant.current=expanded;root.instant.expanded=true;
            var dim=contract(false);dim.added(new Window(root),true,60);var plane=(CardBackdropView)root.getChildAt(0);
            assertEquals(.6f,plane.getAlpha(),.0001f);
            root.instant.current=new View(activity.get());root.instant.expanded=false;expanded.setAlpha(.5f);
            root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(.3f,plane.getAlpha(),.0001f);
            expanded.setVisibility(View.GONE);root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(0,plane.getAlpha(),0);
            dim.refresh(false,60);
        }finally{activity.pause().stop().destroy();}
    }
    @Test public void multipleCardsUseTheLargestVisibleProgressAndZeroAddsNoLayer()throws Exception{
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try{
            var root=new Root(activity.get());activity.get().setContentView(root);layout(root);
            var first=new Card(activity.get());var second=new Card(activity.get());root.cards.addView(first);root.cards.addView(second);
            first.layout(0,0,1000,500);second.layout(0,500,1000,1000);
            var dim=contract(false);var owner=new Window(root);dim.added(owner,true,0);assertEquals(2,root.getChildCount());
            dim.refresh(true,60);var plane=(CardBackdropView)root.getChildAt(0);first.setAlpha(0);
            root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(.6f,plane.getAlpha(),.0001f);
            second.setAlpha(0);root.getViewTreeObserver().dispatchOnPreDraw();assertEquals(0,plane.getAlpha(),0);
            dim.removed(owner);assertEquals(2,root.getChildCount());
        }finally{activity.pause().stop().destroy();}
    }
    private void layout(View view){view.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2000,View.MeasureSpec.EXACTLY));view.layout(0,0,1000,2000);}
    private NativeCardDim contract(boolean old)throws Exception{
        Map<String,Class<?>> types=Map.of(old?"l5.h":"com.oplus.systemui.plugins.seedling.card.window.i",Window.class,
                old?"i5.d":NativeCameraCard.VIEWS+"e",Root.class,NativeCameraCard.VIEWS+"CardView",Card.class,
                NativeCameraCard.VIEWS+"CardContainer",Container.class,NativeCameraCard.VIEWS+"InstantContainer",Instant.class,
                old?"j6.h":"com.oplus.systemui.plugins.common.util.j",Provider.class);
        return new NativeCardDim(name->{Class<?> found=types.get(name);if(found==null)throw new ClassNotFoundException(name);return found;});
    }
    public static class Window{public Root f,g;Window(Root root){f=g=root;}public void a(boolean value){}public void e(){}}
    public static class Root extends FrameLayout{
        public final Rect h=new Rect(460,25,540,115),s=h;final Container cards;final Instant instant;
        Root(Context context){super(context);cards=new Container(context);instant=new Instant(context);addView(cards);addView(instant);}
        public Container getCardContainer(){return cards;}public Instant getInstantContainer(){return instant;}
    }
    public static class Container extends FrameLayout{Container(Context context){super(context);}}
    public static class Card extends FrameLayout{Card(Context context){super(context);}}
    public static class Instant extends FrameLayout{
        View current;boolean expanded;Instant(Context context){super(context);}
        public View getInstantView(){return current;}public boolean l(){return expanded;}
    }
    public static class Provider extends ViewOutlineProvider{
        public Rect a,c;Provider(Rect value){a=c=value;}
        @Override public void getOutline(View view,Outline out){out.setRoundRect(a,20);}
    }
}
