package dev.lsposed.fluidcloud;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36)
public class NativeCameraCardTest {
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void reopeningTheSameLegacyCardReadsTheCurrentRadiusAndCurve() throws Exception {
        var context = Robolectric.buildActivity(Activity.class).setup().get();
        var card = new Card(context);
        var provider = new OutlineProvider(new Rect(0, 0, 260, 180));
        var nativeCards = contract(true);
        var style = new java.util.concurrent.atomic.AtomicReference<>(CloudCorners.Style.OEM);
        var corners = new NativeCardCorners(style::get);
        var outline = new android.graphics.Outline();

        provider.getOutline(card, outline);
        corners.legacyOutline(nativeCards, card, outline, provider);
        assertEquals(0f, outline.getRadius(), 0f);

        style.set(new CloudCorners.Style(true, 20, false));
        provider.getOutline(card, outline);
        corners.legacyOutline(nativeCards, card, outline, provider);
        assertEquals(20 * context.getResources().getDisplayMetrics().density, outline.getRadius(), .01f);

        style.set(new CloudCorners.Style(true, 20, true));
        provider.getOutline(card, outline);
        corners.legacyOutline(nativeCards, card, outline, provider);
        assertFalse(outline.isEmpty());
        assertFalse(outline.getRect(new Rect()));

        style.set(CloudCorners.Style.OEM);
        provider.getOutline(card, outline);
        corners.legacyOutline(nativeCards, card, outline, provider);
        assertTrue(outline.getRect(new Rect()));
        assertEquals(0f, outline.getRadius(), 0f);
    }

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void interruptedRecyclerOverscrollDoesNotMoveTheCameraAnchorOrSettledCard() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);
        var list=new Container(context);root.addView(list);var card=card(context,0,350);list.addView(card);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());
        for (int scroll : new int[]{0,6,12,18,30,60,0}) {
            list.scrollTo(0,scroll);
            var provider=new OutlineProvider(new Rect(100,70,900,400));card.setOutlineProvider(provider);
            card.setScaleX(.75f);card.setScaleY(.75f);contract.beforeDraw(card,true);
            var mask=NativeCameraCard.morph(provider.a,root.h,.75f,.75f,card.getPivotY(),list.getTranslationY()-scroll,
                    card.getTop(),card.getWidth(),card.getHeight());
            var matrix=new android.graphics.Matrix();card.transformMatrixToGlobal(matrix);
            float[] point={mask.mask().left,mask.mask().top};matrix.mapPoints(point);
            assertEquals(25,point[1],.01f);
            card.setScaleX(1);card.setScaleY(1);card.setOutlineProvider(new OutlineProvider(null));
            contract.beforeDraw(card,true);matrix.reset();card.transformMatrixToGlobal(matrix);point=new float[]{0,0};matrix.mapPoints(point);
            assertEquals(25,point[1],.01f);
        }
        list.scrollTo(0,18);card.layout(0,-40,1000,-40+card.getMeasuredHeight());
        contract.beforeDraw(card,true);
        var matrix=new android.graphics.Matrix();card.transformMatrixToGlobal(matrix);float[] point={0,0};matrix.mapPoints(point);
        assertEquals(-29,point[1],.01f); // Actual RecyclerView child scrolling remains native.
        contract.restore();
    }
    @Test public void nativeCleanupAndInterruptedProviderChangesCannotReuseAnOldMorphOffset() throws Exception {
        for (boolean old : new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);root.h.set(460,25,540,115);
            var list=new Container(context);root.addView(list);var card=card(context,0,350);list.addView(card);
            var contract=contract(old);list.setTranslationY(200);contract.afterNativeLayout(list,true);
            card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            card.layout(0,14,1000,14+card.getMeasuredHeight());
            for (int round=0;round<12;round++) {
                var provider=new OutlineProvider(new Rect(100,40+round,900,400));
                card.setOutlineProvider(provider);card.setScaleX(.75f);card.setScaleY(.75f);
                contract.beforeDraw(card,true);
                var actual=NativeCameraCard.morph(provider.a,root.h,.75f,.75f,card.getPivotY(),list.getTranslationY(),card.getTop(),card.getWidth(),card.getHeight());
                assertEquals(actual.translationY(),card.getTranslationY(),.001f);
                card.setScaleX(1);card.setScaleY(1);card.setOutlineProvider(new OutlineProvider(null));
                contract.beforeDraw(card,true);
                assertEquals(0,card.getTranslationY(),.001f);
            }
            contract.restore();
        }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void materialStaysContinuousWhileNativeBodyFadesAndBlursWithoutReparentingControls() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=new Card(context);card.setBackgroundColor(android.graphics.Color.BLACK);
        var body=new FrameLayout(context);body.setBackgroundColor(0xff345678);
        var control=new View(context);control.setBackgroundColor(android.graphics.Color.WHITE);
        body.addView(control,new FrameLayout.LayoutParams(100,100));
        card.i=body;card.s=body;card.addView(body,new FrameLayout.LayoutParams(-1,350));list.addView(card);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());
        body.setAlpha(0f);body.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(12,12,android.graphics.Shader.TileMode.CLAMP));
        contract.beforeDraw(card,true);
        var image=android.graphics.Bitmap.createBitmap(1000,card.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        card.draw(new android.graphics.Canvas(image));
        assertEquals(0xff345678,image.getPixel(500,20));assertEquals(image.getPixel(500,20),image.getPixel(500,220));
        assertSame(card,body.getParent());assertSame(body,control.getParent());assertEquals(0,body.getAlpha(),0);
        assertEquals(0,control.getTop());
        body.setAlpha(1f);body.setRenderEffect(null);contract.beforeDraw(card,true);
        card.draw(new android.graphics.Canvas(image));assertEquals(android.graphics.Color.WHITE,image.getPixel(20,200));
        contract.afterLayout(card,false);assertEquals(1,card.getChildCount());
        assertEquals(0xff345678,((android.graphics.drawable.ColorDrawable)body.getBackground()).getColor());
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void translucentMaterialIsPaintedOnceAndSdkReplacementKeepsItsInvalidationCallback() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=new Card(context);card.setBackgroundColor(android.graphics.Color.WHITE);
        var body=new FrameLayout(context);body.setBackgroundColor(0x80000000);
        body.addView(new View(context),new FrameLayout.LayoutParams(10,10));
        card.i=body;card.s=body;card.addView(body,new FrameLayout.LayoutParams(-1,350));list.addView(card);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.beforeDraw(card,true);
        var image=android.graphics.Bitmap.createBitmap(1000,card.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        card.draw(new android.graphics.Canvas(image));
        assertEquals(127,android.graphics.Color.red(image.getPixel(500,20)),1);
        assertEquals(image.getPixel(500,20),image.getPixel(500,220));
        var replacement=new android.graphics.drawable.ColorDrawable(0xff456789);body.setBackground(replacement);
        contract.beforeDraw(card,true);card.draw(new android.graphics.Canvas(image));
        assertEquals(0xff456789,image.getPixel(500,20));assertSame(body.getBackground(),replacement.getCallback());
        contract.restore();assertSame(replacement,body.getBackground());assertSame(body,replacement.getCallback());
    }
    @Test public void instantTouchSurfaceFailureRestoresOwnershipAndDoesNotEscapePredraw() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);
        var container=new Instant(context);root.addView(container,new FrameLayout.LayoutParams(1000,2400));
        var expanded=new FrameLayout(context);expanded.setPadding(0,7,0,0);
        expanded.addView(new View(context),new FrameLayout.LayoutParams(900,250));
        var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;container.addView(expanded,params);
        container.current=expanded;container.expanded=true;
        var cards=new NativeCameraCard(lookup(false),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
        var instant=new NativeInstantCameraCard(lookup(false),cards,(view,region)->{throw new IllegalStateException("surface lost");});
        int[] failures={0};cards.onFailure=error->{failures[0]++;instant.restore();cards.restore();};
        instant.update(container,true);
        root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));
        root.layout(0,0,1000,2400);instant.update(container,true);
        container.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,failures[0]);
        assertEquals(200,margin(expanded));assertEquals(7,expanded.getPaddingTop());assertFalse(instant.owns(expanded));
        container.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,failures[0]);
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void instantInputFollowsCardFramesInsteadOfStaleSmallCapsuleAndReturnsOnCollapse() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);root.h.set(460,25,540,115);
            var container=new Instant(context);root.addView(container,new FrameLayout.LayoutParams(1000,2400));
            var expanded=new FrameLayout(context);expanded.setPadding(3,7,4,8);
            expanded.addView(new View(context),new FrameLayout.LayoutParams(900,250));
            var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;container.addView(expanded,params);
            container.current=expanded;container.expanded=true;
            var cards=new NativeCameraCard(lookup(old),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
            var written=new java.util.ArrayList<android.graphics.Region>();
            var instant=new NativeInstantCameraCard(lookup(old),cards,(view,region)->{written.add(new android.graphics.Region(region));return true;});
            instant.update(container,true);
            root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));
            root.layout(0,0,1000,2400);instant.update(container,true);
            var stale=new android.graphics.Region(460,25,540,115);
            assertTrue("bounds="+instant.inputRegion(container,stale,true).getBounds()+" mapped="+cards.instantTouchBounds(expanded)
                    +" root="+expanded.getRootView().getWidth()+","+expanded.getRootView().getHeight()
                    +" card="+expanded.getLeft()+","+expanded.getTop()+","+expanded.getWidth()+","+expanded.getHeight(),
                    instant.inputRegion(container,stale,true).contains(500,350));
            assertFalse(stale.contains(500,350));
            assertSame(stale,instant.inputRegion(container,stale,false));
            container.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(1,written.size());assertTrue(written.getLast().contains(500,350));
            assertFalse(written.getLast().contains(500,500));
            container.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,written.size());
            expanded.setScaleX(.5f);expanded.setScaleY(.5f);
            container.getViewTreeObserver().dispatchOnPreDraw();assertEquals(2,written.size());
            assertEquals(500,written.getLast().getBounds().width());
            assertEquals(220,written.getLast().getBounds().height());
            instant.nativeChanged(container);container.getViewTreeObserver().dispatchOnPreDraw();assertEquals(3,written.size());
            var small=new FrameLayout(context);container.addView(small,new FrameLayout.LayoutParams(200,60));small.layout(400,25,600,85);
            container.current=small;container.expanded=false;instant.update(container,true);
            container.getViewTreeObserver().dispatchOnPreDraw();
            assertEquals(new Rect(400,25,600,85),written.getLast().getBounds());
            int count=written.size();container.getViewTreeObserver().dispatchOnPreDraw();assertEquals(count,written.size());
            instant.detached(container);instant.restore();cards.restore();
        }
    }
    @Test public void instantDecorationCannotMakeWrapContentCardFillTheScreen() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);
        var container=new Instant(context);root.addView(container,new FrameLayout.LayoutParams(1000,2400));
        var expanded=new FrameLayout(context);expanded.setPadding(3,7,4,8);
        expanded.addView(new View(context),new FrameLayout.LayoutParams(900,250));
        var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;container.addView(expanded,params);
        container.current=expanded;container.expanded=true;
        var cards=new NativeCameraCard(lookup(false),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
        var instant=new NativeInstantCameraCard(lookup(false),cards);
        for(int round=0;round<4;round++) {
            instant.update(container,true);
            container.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));
            container.layout(0,0,1000,2400);instant.update(container,true);
            assertEquals(250+182+8,expanded.getMeasuredHeight());
            assertEquals(250+182+8,expanded.getHeight());
            assertEquals(2,expanded.getChildCount());
            View decoration=expanded.getChildAt(1);
            assertEquals(expanded.getWidth(),decoration.getWidth());
            assertEquals(expanded.getHeight(),decoration.getHeight());
        }
        instant.restore();cards.restore();
    }
    @Test public void layoutTransitionIntermediateBackgroundTopsNeverAccumulateTheCameraInset() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=new Card(context);var body=new FrameLayout(context);var media=new MediaBackground(context);
        var page=new FrameLayout(context);page.setBackgroundColor(0xff123456);
        body.addView(page,new FrameLayout.LayoutParams(-1,-1));body.addView(media,new FrameLayout.LayoutParams(-1,-1));
        card.i=body;card.s=body;card.addView(body,new FrameLayout.LayoutParams(-1,350));list.addView(card);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.beforeDraw(card,true);
        int[] layoutEvents={0};media.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layoutEvents[0]++);
        page.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->layoutEvents[0]++);
        for(int top:new int[]{-188,-150,-80,-1,0,-189,-300}) {
            media.setTop(top);page.setTop(top); // OEM LayoutTransition changes only the frame.
            contract.beforeDraw(card,true);
            assertEquals(-189,media.getTop());assertEquals(539,media.getHeight());
            assertEquals(-189,page.getTop());assertEquals(539,page.getHeight());
        }
        assertEquals(0,layoutEvents[0]); // module must not create another changing-layout tween
        body.layout(0,body.getTop(),1000,body.getTop()+370);contract.beforeDraw(card,true);
        assertEquals(-189,media.getTop());assertEquals(559,media.getHeight());
        contract.afterLayout(card,false);
        assertEquals(0,media.getTop());assertEquals(370,media.getHeight());
        assertEquals(0,page.getTop());assertEquals(370,page.getHeight());
    }
    @Test public void predrawContractFailureRestoresGeometryWithoutEscapingIntoSystemUi() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=card(context,7,350);list.addView(card);var contract=contract(false);int[] failures={0};
        contract.onFailure=error->failures[0]++;
        list.setTranslationY(200);contract.afterNativeLayout(list,true);list.failPositions=true;
        assertFalse(list.getViewTreeObserver().dispatchOnPreDraw());
        assertEquals(1,failures[0]);assertEquals(200,list.getTranslationY(),0);assertEquals(7,margin(card.i));
        list.getViewTreeObserver().dispatchOnPreDraw();assertEquals(1,failures[0]);
    }
    @Test public void nativeContentResizeAnimationIncludesOwnedHeaderAndLeavesOtherItemsNative() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
            var first=card(context,7,350);var second=card(context,0,350);list.addView(first);list.addView(second);
            var contract=contract(old);list.setTranslationY(200);contract.afterNativeLayout(list,true);
            assertEquals(539,contract.intrinsicHeight(first,350,true));
            assertEquals(569,contract.intrinsicHeight(first,380,true));
            assertEquals(350,contract.intrinsicHeight(first,350,false));
            assertEquals(350,contract.intrinsicHeight(second,350,true));
            assertEquals(0,contract.intrinsicHeight(first,0,true));
            contract.beforeMeasure(first,false);assertEquals(350,contract.intrinsicHeight(first,350,true));
            first.i=null;first.s=null;assertEquals(539,contract.intrinsicHeight(first,539,true));
            contract.restore();
        }
    }
    @Test public void childOnlySdkReplacementAndClipResetAreRepairedBeforeDrawingParentAgain() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=templateCard(context);list.addView(card);var contract=contract(false);
        list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.beforeDraw(card,true);
        var page=(FrameLayout)((FrameLayout)card.i).getChildAt(0);
        var material=page.getChildAt(0);var replacement=new android.graphics.drawable.GradientDrawable();
        material.setForeground(replacement);page.setClipToOutline(true);card.setClipChildren(true);
        list.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(replacement,((CardHeaderDrawable)material.getForeground()).nativeDrawable);
        assertSame(material.getForeground(),replacement.getCallback());
        assertFalse(page.getClipToOutline());assertFalse(card.getClipChildren());
        contract.restore();assertSame(replacement,material.getForeground());
    }
    @Test public void instantExpandedRootUsesCameraAnchorAndPreservesContentAndOutgoingNativeAnimation() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);root.h.set(460,25,540,115);var container=new Instant(context);root.addView(container);
            var expanded=new FrameLayout(context);expanded.setPadding(3,7,4,8);
            expanded.addView(new View(context),new FrameLayout.LayoutParams(100,350));
            var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;container.addView(expanded,params);
            container.current=expanded;container.expanded=true;
            var cards=new NativeCameraCard(lookup(old),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
            var instant=new NativeInstantCameraCard(lookup(old),cards);
            for(int i=0;i<5;i++){instant.update(container,true);instant.afterStateChange(container,true);}
            assertEquals(25,margin(expanded));assertEquals(182,expanded.getPaddingTop());
            assertEquals(207,margin(expanded)+expanded.getPaddingTop());assertTrue(instant.owns(expanded));assertTrue(cards.handles(expanded));
            expanded.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            expanded.layout(0,25,1000,25+expanded.getMeasuredHeight());instant.update(container,true);
            assertEquals(350+182+8,expanded.getHeight());
            expanded.setScaleX(.4f);expanded.setScaleY(.4f);
            var outline=new OutlineProvider(new Rect(450,220,550,320));cards.beforeOutline(outline,expanded,true);cards.align(expanded,true);
            float top=expanded.getTop()+expanded.getTranslationY()+expanded.getPivotY()+(outline.a.top-expanded.getPivotY())*.4f;
            assertEquals(25,top,.001f);
            var small=new FrameLayout(context);container.addView(small,new FrameLayout.LayoutParams(100,50));
            container.current=small;container.expanded=false;instant.update(container,true);
            assertTrue(instant.owns(expanded));assertFalse(instant.owns(small));assertEquals(0,small.getPaddingTop());
            container.removeView(expanded);instant.update(container,true);
            assertFalse(instant.owns(expanded));assertFalse(cards.handles(expanded));
            assertEquals(200,margin(expanded));assertEquals(7,expanded.getPaddingTop());
            assertEquals(1,expanded.getChildCount());instant.restore();cards.restore();
        }
    }
    @Test public void automaticInstantAppearWithReusedZeroMarginMatchesManualCameraPosition() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);root.h.set(460,25,540,115);
            var container=new Instant(context);root.addView(container);
            var view=new FrameLayout(context);view.setPadding(3,7,4,8);
            view.addView(new View(context),new FrameLayout.LayoutParams(100,350));
            container.addView(view,new FrameLayout.LayoutParams(1000,-2));
            container.current=view;container.expanded=true;
            var cards=new NativeCameraCard(lookup(old),(c,y)->NativeCameraCard.plan(root.h,
                    new Rect(470,30,530,100),1000,120,y,0,true,true));
            var instant=new NativeInstantCameraCard(lookup(old),cards);
            instant.afterStateChange(container,true);
            assertEquals("Auto appearance must acquire the same native position as a manual open",25,margin(view));
            assertEquals(182,view.getPaddingTop());
            assertEquals(207,margin(view)+view.getPaddingTop());
            assertEquals(1,container.positionWrites);
            for(int i=0;i<5;i++){instant.update(container,true);instant.afterStateChange(container,true);}
            assertEquals(1,container.positionWrites);
            instant.update(container,false);
            assertEquals(200,margin(view));assertEquals(7,view.getPaddingTop());
            instant.restore();cards.restore();
        }
    }
    @Test public void automaticInstantPositionIsPreparedBeforeItsCameraAnchorArrives() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);var container=new Instant(context);root.addView(container);
            var view=new FrameLayout(context);view.setPadding(3,7,4,8);
            container.addView(view,new FrameLayout.LayoutParams(1000,-2));container.current=view;container.expanded=true;
            var cards=new NativeCameraCard(lookup(old),(c,y)->NativeCameraCard.plan(root.h,
                    new Rect(470,30,530,100),1000,120,y,0,true,true));
            var instant=new NativeInstantCameraCard(lookup(old),cards);
            instant.afterStateChange(container,true);
            assertEquals(200,margin(view));assertEquals(7,view.getPaddingTop());assertFalse(instant.owns(view));
            root.h.set(460,25,540,115);instant.update(container,true);
            assertEquals(25,margin(view));assertEquals(182,view.getPaddingTop());
            assertEquals(1,container.positionWrites);
            instant.restore();cards.restore();
        }
    }
    @Test public void instantAppearSceneCapturesThePaddedHeightBeforeBuildingItsSizeAnimation() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);
        var container=new Instant(context);root.addView(container,new FrameLayout.LayoutParams(1000,2400));
        var view=new FrameLayout(context);view.setPadding(3,7,4,8);
        view.addView(new View(context),new FrameLayout.LayoutParams(100,350));
        var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;
        container.addView(view,params);container.current=view;container.expanded=true;
        container.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));
        container.layout(0,0,1000,2400);
        assertEquals(365,view.getMeasuredHeight());
        var cards=new NativeCameraCard(lookup(false),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
        var instant=new NativeInstantCameraCard(lookup(false),cards);
        instant.afterStateChange(container,true);
        assertEquals("A requested layout still exposes the old native animation target",365,view.getMeasuredHeight());
        instant.beforeAppearScene(container,true);
        assertEquals(540,view.getMeasuredHeight());assertEquals(540,view.getHeight());
        assertEquals(25,view.getTop());assertEquals(182,view.getChildAt(0).getTop());
        assertEquals(207,view.getTop()+view.getChildAt(0).getTop());
        for(int i=0;i<5;i++)instant.beforeAppearScene(container,true);
        assertEquals(540,view.getMeasuredHeight());assertEquals(182,view.getPaddingTop());
        instant.restore();cards.restore();
    }
    @Test public void instantAppearPreparationLeavesNativeOrUnreadyRootsUnmeasured() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);
        var container=new Instant(context);root.addView(container);
        var view=new FrameLayout(context);view.addView(new View(context),new FrameLayout.LayoutParams(100,350));
        var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;
        container.addView(view,params);container.current=view;container.expanded=true;
        var cards=new NativeCameraCard(lookup(false),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
        var instant=new NativeInstantCameraCard(lookup(false),cards);
        instant.beforeAppearScene(container,false);
        assertEquals(0,view.getMeasuredHeight());assertEquals(0,view.getPaddingTop());assertFalse(instant.owns(view));
        container.expanded=false;instant.beforeAppearScene(container,true);
        assertEquals(0,view.getMeasuredHeight());assertFalse(instant.owns(view));
        container.expanded=true;instant.beforeAppearScene(container,true);
        assertEquals("An unattached, unsized native window must wait for its normal layout",0,view.getMeasuredHeight());
        instant.restore();cards.restore();
    }
    @Test public void instantPaintLayersRecoverFromLayoutOnlyPaddingOffsetsWithoutMovingContent() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);root.h.set(460,25,540,115);
            var container=new Instant(context);root.addView(container,new FrameLayout.LayoutParams(1000,2400));
            var view=new FrameLayout(context);view.setPadding(3,7,4,8);
            var body=new FrameLayout(context);body.setBackgroundColor(0xff345678);
            body.addView(new View(context),new FrameLayout.LayoutParams(100,350));
            view.addView(body,new FrameLayout.LayoutParams(-1,350));
            var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;
            container.addView(view,params);container.current=view;container.expanded=true;
            container.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY));
            container.layout(0,0,1000,2400);
            var cards=new NativeCameraCard(lookup(old),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
            var instant=new NativeInstantCameraCard(lookup(old),cards);
            instant.beforeAppearScene(container,true);
            int layers=0;
            for(int i=0;i<view.getChildCount();i++) {
                View child=view.getChildAt(i);
                if(child instanceof CameraMaterialSurface || child instanceof CameraHeaderSurface) {
                    layers++;
                    // FrameLayout can lay out a paint-only child at paddingTop
                    // using its already prepared measured size, without a new measure.
                    child.layout(3,182,3+view.getWidth(),182+view.getHeight());
                }
            }
            assertEquals(2,layers);
            // Native size/scale callbacks keep running without redispatching
            // the parent. Repair in that frame, not at the next container draw.
            cards.updateSurface(view,true);
            for(int i=0;i<view.getChildCount();i++) {
                View child=view.getChildAt(i);
                if(child instanceof CameraMaterialSurface || child instanceof CameraHeaderSurface) {
                    assertEquals(0,child.getLeft());assertEquals(0,child.getTop());
                    assertEquals(view.getWidth(),child.getRight());assertEquals(view.getHeight(),child.getBottom());
                }
            }
            assertEquals(182,body.getTop());assertEquals(350,body.getHeight());
            instant.restore();cards.restore();
        }
    }
    @Test public void automaticInstantPositionRefreshDoesNotTouchDisabledSmallOrLandscapeCards() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);var container=new Instant(context);root.addView(container);
        var view=new FrameLayout(context);container.addView(view,new FrameLayout.LayoutParams(1000,-2));
        container.current=view;container.expanded=true;
        var cards=new NativeCameraCard(lookup(false),(c,y)->null);
        var instant=new NativeInstantCameraCard(lookup(false),cards);
        instant.afterStateChange(container,false);assertEquals(0,margin(view));assertEquals(0,container.positionWrites);
        container.expanded=false;instant.afterStateChange(container,true);assertEquals(0,container.positionWrites);
        container.expanded=true;
        int orientation=context.getResources().getConfiguration().orientation;
        context.getResources().getConfiguration().orientation=android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        try {instant.afterStateChange(container,true);assertEquals(0,container.positionWrites);}
        finally {context.getResources().getConfiguration().orientation=orientation;}
        var keyguard=context.getSystemService(android.app.KeyguardManager.class);
        org.robolectric.Shadows.shadowOf(keyguard).setKeyguardLocked(true);
        try {instant.afterStateChange(container,true);assertEquals(0,container.positionWrites);}
        finally {org.robolectric.Shadows.shadowOf(keyguard).setKeyguardLocked(false);}
        instant.restore();cards.restore();
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void automaticInstantResetWithoutAnimatedOutlineKeepsTheCameraAnchor() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();
            var root=new Root(context);root.h.set(460,25,540,115);
            var container=new Instant(context);root.addView(container);
            var view=new FrameLayout(context);view.addView(new View(context),new FrameLayout.LayoutParams(100,350));
            var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;container.addView(view,params);
            container.current=view;container.expanded=true;
            var cards=new NativeCameraCard(lookup(old),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
            var instant=new NativeInstantCameraCard(lookup(old),cards);instant.update(container,true);
            root.layout(0,0,1000,2400);container.layout(0,0,1000,2400);
            view.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            view.layout(0,25,1000,25+view.getMeasuredHeight());
            for(float scale:new float[]{.4f,.8f,1.03f,1f}) {
                view.setScaleX(scale);view.setScaleY(scale);
                view.setOutlineProvider(new OutlineProvider(null));
                // The direct-appear scene and its cleanup can write a translation
                // before the first SizeFragment, or after replacing its outline.
                float y=cards.translation(view,-240,true);
                float top=view.getTop()+y+view.getPivotY()*(1-scale);
                assertEquals("Unbounded instant outline must still start at the camera",25,top,.001f);
                view.setTranslationY(y);
                Rect touch=cards.instantTouchBounds(view);assertNotNull(touch);
                assertEquals("Touch bounds must discard the previous scene's clip too",25,touch.top,1f);
                assertEquals(-240,cards.translation(view,-240,false),0f);
                var animated=new OutlineProvider(new Rect(450,200,550,300));
                view.setOutlineProvider(animated);cards.beforeOutline(animated,view,true);
                y=cards.translation(view,-240,true);
                top=view.getTop()+y+view.getPivotY()+(animated.a.top-view.getPivotY())*scale;
                assertEquals(25,top,.001f);
            }
            instant.restore();cards.restore();
        }
    }
    @Test public void instantOptOutAndNativeUpdatesRestoreOnlyOwnedPaddingAndPosition() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);var container=new Instant(context);root.addView(container);
        var view=new FrameLayout(context);view.setPadding(3,7,4,8);
        var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;container.addView(view,params);
        container.current=view;container.expanded=true;
        var cards=new NativeCameraCard(lookup(false),(c,y)->new NativeCameraCard.Plan(25,Math.round(y-25)));
        var instant=new NativeInstantCameraCard(lookup(false),cards);instant.update(container,true);
        view.setPadding(3,11,4,8);params.topMargin=210;instant.update(container,true);
        assertEquals(196,view.getPaddingTop());instant.update(container,false);
        assertEquals(210,margin(view));assertEquals(11,view.getPaddingTop());assertFalse(instant.owns(view));
        var unknown=new View(context);container.addView(unknown,new FrameLayout.LayoutParams(1000,300));container.current=unknown;
        instant.update(container,true);assertFalse(instant.owns(unknown));assertEquals(0,unknown.getPaddingTop());
        instant.detached(container);cards.restore();
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void settledMaterialAndNativeSpotlightSpanCameraHeaderWithoutASecondMask() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var root=new Root(context);root.h.set(460,25,540,115);
        var list=new Container(context);root.addView(list);
        var card=new HighlightCard(context);var body=new FrameLayout(context);body.setBackgroundColor(0xffeeeeee);
        body.addView(new View(context),new FrameLayout.LayoutParams(100,350));
        card.i=body;card.s=body;card.addView(body,new FrameLayout.LayoutParams(-1,350));list.addView(card);
        var nativeCover=new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK);
        nativeCover.setAlpha(173);card.setForeground(nativeCover);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.beforeDraw(card,true);
        assertTrue(card.getForeground() instanceof CameraCardForeground);
        assertEquals(173,nativeCover.getAlpha()); // the OEM animator state is never clamped/reset
        nativeCover.setAlpha(64);int nativeAnimatedAlpha=nativeCover.getAlpha();
        assertEquals(nativeAnimatedAlpha,card.getForeground().getAlpha());
        assertEquals(android.graphics.PixelFormat.TRANSPARENT,card.getForeground().getOpacity());
        var header=card.getChildAt(card.getChildCount()-1);
        assertTrue(header instanceof CameraHeaderSurface);assertFalse(header.isClickable());assertFalse(header.isFocusable());
        var image=android.graphics.Bitmap.createBitmap(1000,card.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
        card.draw(new android.graphics.Canvas(image));
        // The physical hole remains black on the real display; added pixels
        // surrounding it must show the same native material as the body.
        for(int y=20;y<189;y++) assertEquals(238,android.graphics.Color.red(image.getPixel(500,y)),1);
        assertEquals(238,android.graphics.Color.red(image.getPixel(500,189)),1);
        card.highlight=true;card.draw(new android.graphics.Canvas(image));
        assertTrue(android.graphics.Color.red(image.getPixel(500,20))>20);
        assertEquals(android.graphics.Color.red(image.getPixel(500,188)),android.graphics.Color.red(image.getPixel(500,190)),2);
        contract.afterLayout(card,false);
        assertSame(nativeCover,card.getForeground());assertEquals(nativeAnimatedAlpha,nativeCover.getAlpha());
        assertEquals(1,card.getChildCount());assertSame(body,card.getChildAt(0));
    }
    @Test public void cameraHeaderKeepsContentClearAndNativeAnimationEndpointsAgree() throws Exception {
        var activity=Robolectric.buildActivity(Activity.class).setup().visible();
        try {
            var root=new Root(activity.get());var list=new Container(activity.get());root.addView(list);
            activity.get().setContentView(root);
            var card=card(activity.get(),7,350);list.addView(card);
            var bg=new View(activity.get());card.addView(bg,0,new FrameLayout.LayoutParams(-1,-1));
            var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
            contract.beforeMeasure(card,true);
            card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            card.layout(0,14,1000,14+card.getMeasuredHeight());
            assertEquals(11,list.getTranslationY(),0);assertEquals(546,card.getMeasuredHeight());
            assertEquals(546,bg.getMeasuredHeight());
            assertEquals(200+14+7,list.getTranslationY()+card.getTop()+card.i.getTop(),0);
            float center=list.getTranslationY()+card.getTop()+card.getMeasuredHeight()/2f;
            float nativeStartTranslation=70-center;
            assertEquals(70,center+nativeStartTranslation,0); // appear starts / disappear ends here
            assertEquals(25,list.getTranslationY()+card.getTop(),0); // expanded top overlaps the camera band
            contract.restore();assertEquals(200,list.getTranslationY(),0);assertEquals(7,margin(card.i));
        } finally { activity.pause().stop().destroy(); }
    }
    @Test public void repeatedLayoutAndMeasurementDoNotAccumulateAndOnlyFirstItemGrows() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();
        var list=new Container(context);var first=card(context,7,350);var second=card(context,4,200);
        list.addView(first);list.addView(second);var contract=contract(false);
        for(int i=0;i<6;i++) {
            list.setTranslationY(200);contract.afterNativeLayout(list,true);
            contract.beforeMeasure(first,true);contract.beforeMeasure(first,true);contract.beforeMeasure(second,true);
            assertEquals(11,list.getTranslationY(),0);assertEquals(196,margin(first.i));assertEquals(4,margin(second.i));
        }
        list.removeView(first);list.addView(first); // reused first holder is now a later item
        contract.beforeMeasure(first,true);contract.beforeMeasure(second,true);
        assertEquals(7,margin(first.i));assertEquals(193,margin(second.i));
    }
    @Test public void transientAdapterInvalidationMustNotRemoveTheHeaderDuringRapidReentry() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=card(context,7,350);list.addView(card);var contract=contract(false);
        list.setTranslationY(200);contract.afterNativeLayout(list,true);assertEquals(196,margin(card.i));
        list.adapterOverride=-1;list.layoutOverride=0;
        contract.beforeMeasure(card,true);assertEquals(196,margin(card.i));
        list.layoutOverride=-1;contract.beforeMeasure(card,true);assertEquals(196,margin(card.i));
        contract.afterLayout(card,true); // unknown position must not discard a running presentation
        list.removeView(card);contract.beforeMeasure(card,true);assertEquals(196,margin(card.i));
        contract.afterLayout(card,true);list.addView(card);
        list.adapterOverride=1;contract.beforeMeasure(card,true);assertEquals(7,margin(card.i));
    }
    @Test public void optOutDetachAndRotationRestoreOwnedGeometryWithoutOverwritingNativeUpdates() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=card(context,5,300);list.addView(card);boolean[] portrait={true};
        var contract=new NativeCameraCard(lookup(true),(view,y)->plan(y,portrait[0]));
        list.setTranslationY(200);contract.afterNativeLayout(list,true);assertEquals(194,margin(card.i));
        portrait[0]=false;contract.beforeMeasure(card,true);
        assertEquals(200,list.getTranslationY(),0);assertEquals(5,margin(card.i));
        portrait[0]=true;list.setTranslationY(210);contract.afterNativeLayout(list,true);
        assertEquals(204,margin(card.i));
        contract.beforeMeasure(card,false);assertEquals(210,list.getTranslationY(),0);assertEquals(5,margin(card.i));
        list.setTranslationY(200);contract.afterNativeLayout(list,true);
        ((FrameLayout.LayoutParams)card.i.getLayoutParams()).topMargin=9;
        contract.restore();assertEquals(9,margin(card.i));assertEquals(200,list.getTranslationY(),0);
    }
    @Test public void sideOrUnknownCutoutsLockscreenAndInvalidOffsetsKeepNativeGeometry() {
        Rect capsule=new Rect(460,25,540,115),cutout=new Rect(461,0,539,141);
        assertNotNull(NativeCameraCard.plan(capsule,cutout,1000,150,200,14,true,true));
        assertNull(NativeCameraCard.plan(capsule,cutout,1000,150,200,14,false,true));
        assertNull(NativeCameraCard.plan(capsule,cutout,1000,150,200,14,true,false));
        assertNull(NativeCameraCard.plan(capsule,new Rect(0,200,90,300),1000,150,200,14,true,true));
        assertNull(NativeCameraCard.plan(capsule,new Rect(),1000,150,200,14,true,true));
        assertNull(NativeCameraCard.plan(null,cutout,1000,150,200,14,true,true));
        assertNull(NativeCameraCard.plan(capsule,cutout,1000,150,Float.NaN,14,true,true));
        assertNull(NativeCameraCard.plan(capsule,cutout,1000,150,1000,14,true,true));
        assertNull(NativeCameraCard.plan(capsule,cutout,1000,150,0,14,true,true));
    }
    @Test public void everyMorphFrameUsesCameraTopAndMinimumCameraShapeAcrossInterruptions() {
        Rect camera=new Rect(598,31,672,136);
        // Native appear, disappear, interrupted reappearance and rebound states.
        for (float[] frame : new float[][]{{.4f,142,142},{.65f,430,310},{.9f,1000,540},
                {1.03f,1160,642},{1,1160,642},{.72f,780,450},{.41f,146,146},{.58f,340,240}}) {
            int w=(int)frame[1],h=(int)frame[2];
            Rect mask=new Rect((1160-w)/2,(642-h)/2,(1160+w)/2,(642+h)/2);
            var morph=NativeCameraCard.morph(mask,camera,frame[0],frame[0],321,3,28,1160,642);
            assertNotNull(morph);
            float renderedTop=3+28+morph.translationY()+321+(morph.mask().top-321)*frame[0];
            assertEquals(31,renderedTop,.001f);
            assertTrue(morph.mask().height()*frame[0]>=105-.001f);
            assertTrue(morph.mask().width()*frame[0]>=105-.001f);
        }
    }
    @Test public void completedMorphHasNoRestingOffsetAndUnknownGeometryKeepsNativeTransform() {
        var morph=NativeCameraCard.morph(new Rect(0,0,1160,642),new Rect(598,31,672,136),1,1,321,3,28,1160,642);
        assertEquals(0,morph.translationY(),0);assertEquals(new Rect(0,0,1160,642),morph.mask());
        assertNull(NativeCameraCard.morph(new Rect(),new Rect(598,31,672,136),1,1,321,3,28,1160,642));
        assertNull(NativeCameraCard.morph(new Rect(0,0,100,100),new Rect(598,31,672,136),0,0,321,3,28,1160,642));
    }
    @Test public void liveMorphSharesBlurOutlineBoundsAndStopsWhenDisabledOrMovedToAnotherSlot() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var root=new Root(context);
        root.h.set(460,25,540,115);var list=new Container(context);root.addView(list);
        var card=card(context,7,350);list.addView(card);var contract=contract(false);
        list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());card.setScaleX(.4f);card.setScaleY(.4f);
        Rect mask=new Rect(450,223,550,323),original=new Rect(mask);
        contract.beforeBlurBounds(card,mask,false);assertEquals(original,mask);
        contract.beforeBlurBounds(card,mask,true);
        var provider=new OutlineProvider(new Rect(original));contract.beforeOutline(provider,card,true);
        assertTrue(provider.a.width()*.4f>=90);assertTrue(provider.a.height()*.4f>=90);
        contract.align(card,true);
        float top=list.getTranslationY()+card.getTop()+card.getTranslationY()+card.getPivotY()+(provider.a.top-card.getPivotY())*.4f;
        assertEquals(25,top,.001f);assertEquals(123,contract.translation(card,123,false),0);
        list.adapterOverride=1;contract.beforeBlurBounds(card,original,true);
        assertEquals(123,contract.translation(card,123,true),0);
    }
    @Test public void separateNativeOutlineAndBlurRectsMustCoverTheSameMinimumCameraShape() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var context=Robolectric.buildActivity(Activity.class).setup().get();var root=new Root(context);
            root.h.set(460,25,540,115);var list=new Container(context);root.addView(list);
            var card=card(context,7,350);list.addView(card);var contract=contract(old);
            list.setTranslationY(200);contract.afterNativeLayout(list,true);
            card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            card.layout(0,14,1000,14+card.getMeasuredHeight());card.setScaleX(.4f);card.setScaleY(.4f);
            Rect original=new Rect(450,223,550,323),blur=new Rect(original);blur.inset(1,1);
            var provider=new OutlineProvider(new Rect(original));contract.beforeBlurBounds(card,blur,true);
            assertFalse(provider.a.contains(blur)); // prior adaptation left the larger blur exposed as a ring
            contract.beforeOutline(provider,card,true);
            assertTrue(provider.a.contains(blur));Rect inner=new Rect(provider.a);inner.inset(1,1);assertEquals(inner,blur);
            android.graphics.Outline result=new android.graphics.Outline();provider.getOutline(card,result);
            Rect actual=new Rect();assertTrue(result.getRect(actual));assertEquals(provider.a,actual);
        }
    }
    @Test public void cameraSizedSurfaceIsOpaqueBlackAndRestoresNativeBlurAndShadowAsItOpens() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var root=new Root(context);root.h.set(460,25,540,115);
        var list=new Container(context);root.addView(list);var card=card(context,7,350);list.addView(card);
        var background=new BlurBackground(context);card.addView(background,0,new FrameLayout.LayoutParams(-1,-1));
        card.setElevation(56);card.setAlpha(.8f);var contract=contract(false);
        list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());card.setScaleX(.4f);card.setScaleY(.4f);
        Rect mask=new Rect(450,223,550,323);var provider=new OutlineProvider(mask);
        contract.beforeOutline(provider,card,true);contract.align(card,true);
        assertEquals(0,card.getElevation(),.001f);assertEquals(0,background.blurAlpha,.001f);
        var canvas=new SurfaceCanvas();
        contract.drawSurface(card,canvas,true);assertEquals(android.graphics.Color.BLACK,canvas.color);
        card.setScaleX(1);card.setScaleY(1);contract.beforeBlurBounds(card,new Rect(0,0,1000,546),true);contract.align(card,true);
        assertEquals(56,card.getElevation(),0);assertEquals(.8f,background.blurAlpha,0);
        assertEquals(.8f,card.getAlpha(),0); // original alpha / lifecycle remain native
    }
    @Test public void bothFirmwareContractsRequireMeasuredCentersAndNativeRootAnchor() throws Exception {
        for(boolean old:new boolean[]{false,true}) {
            var contract=new NativeCameraCard(lookup(old));assertEquals(old,contract.legacy);
            assertEquals(old?"i":"k",contract.layout.getName());
            assertEquals("getItemViewsCenter",contract.centers.getName());
        }
        try { new NativeCameraCard(name->Object.class);fail("Unknown classes must not install partial hooks"); }
        catch(NoSuchMethodException expected) { }
    }
    @Test public void decorativeMediaBackgroundSpansHeaderWithoutMovingContentAndRestores() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=new Card(context);var wrapper=new FrameLayout(context);var background=new MediaBackground(context);
        wrapper.addView(background,new FrameLayout.LayoutParams(-1,-1));
        var body=new View(context);wrapper.addView(body,new FrameLayout.LayoutParams(100,350));
        card.addView(wrapper,new FrameLayout.LayoutParams(-1,350));card.i=wrapper;card.s=wrapper;list.addView(card);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.afterLayout(card,true);
        assertEquals(-189,background.getTop());assertEquals(539,background.getHeight());
        assertEquals(0,body.getTop());assertEquals(189,wrapper.getTop());
        assertFalse(wrapper.getClipChildren());assertFalse(wrapper.getClipToPadding());assertFalse(card.getClipChildren());
        contract.afterLayout(card,true);assertEquals(-189,background.getTop());assertEquals(539,background.getHeight());
        list.setTranslationY(210);contract.afterNativeLayout(list,true);contract.afterLayout(card,true);
        assertEquals(-199,background.getTop());assertEquals(549,background.getHeight());
        contract.afterLayout(card,false);assertEquals(0,background.getTop());assertEquals(350,background.getHeight());
        assertTrue(wrapper.getClipChildren());assertTrue(wrapper.getClipToPadding());
        assertTrue(card.getClipChildren());
    }
    @Test public void anonymousFullPageDecorationLayersExtendTogetherWithoutMovingMultipleCardContents() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var first=templateCard(context);var second=templateCard(context);list.addView(first);list.addView(second);
        var page=(FrameLayout)((FrameLayout)first.i).getChildAt(0);
        var background=page.getChildAt(0);var body=page.getChildAt(1);var glass=page.getChildAt(2);
        var foreground=background.getForeground();page.setClipToOutline(true);first.setClipToOutline(true);
        var contract=contract(false);
        list.setTranslationY(200);contract.afterNativeLayout(list,true);
        for(int i=0;i<4;i++) {
            contract.beforeMeasure(first,true);contract.beforeMeasure(second,true);
            first.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            first.layout(0,14,1000,14+first.getMeasuredHeight());contract.afterLayout(first,true);
            assertEquals(196,margin(first.i));assertEquals(0,body.getTop());
            assertEquals(-189,background.getTop());assertEquals(539,background.getHeight());
            assertEquals(-189,glass.getTop());assertEquals(539,glass.getHeight());
            assertSame(foreground,((CardHeaderDrawable)background.getForeground()).nativeDrawable);assertFalse(page.getClipToOutline());
            assertTrue(first.getClipToOutline());assertEquals(7,margin(second.i));
        }
        assertEquals(200+14+7,list.getTranslationY()+first.getTop()+first.i.getTop()+body.getTop(),0);
        list.removeView(first);list.addView(first); // material owner moves to a later slot
        contract.beforeMeasure(first,true);contract.beforeMeasure(second,true);contract.afterLayout(first,true);
        assertEquals(7,margin(first.i));assertEquals(196,margin(second.i));
        assertEquals(0,background.getTop());assertEquals(350,background.getHeight());assertTrue(page.getClipToOutline());
        contract.restore();assertEquals(7,margin(second.i));
    }
    private static Card templateCard(Context context) {
        var card=new Card(context);var wrapper=new FrameLayout(context);var page=new FrameLayout(context);
        var material=new FrameLayout(context);material.setBackgroundColor(0xff123456);
        material.setForeground(new android.graphics.drawable.GradientDrawable());
        page.addView(material,new FrameLayout.LayoutParams(-1,-1));
        var body=new FrameLayout(context);body.addView(new View(context),new FrameLayout.LayoutParams(100,100));
        page.addView(body,new FrameLayout.LayoutParams(-1,-1));
        var glass=new FrameLayout(context);glass.setBackgroundColor(0xff234567);
        page.addView(glass,new FrameLayout.LayoutParams(-1,-1));wrapper.addView(page,new FrameLayout.LayoutParams(-1,350));
        var params=new FrameLayout.LayoutParams(-1,-2);params.topMargin=7;
        card.addView(wrapper,params);card.i=wrapper;card.s=wrapper;return card;
    }
    @Test public void movedFirstHolderIsCleanedBeforeDrawingEvenWithoutNewMeasurement() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var first=templateCard(context);var second=templateCard(context);list.addView(first);list.addView(second);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        first.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        first.layout(0,14,1000,14+first.getMeasuredHeight());contract.afterLayout(first,true);
        list.removeView(first);list.addView(first);
        assertTrue(list.getViewTreeObserver().dispatchOnPreDraw()); // returns whether the draw was cancelled
        assertEquals(7,margin(first.i));assertEquals(196,margin(second.i));
        assertEquals(0,((FrameLayout)((FrameLayout)first.i).getChildAt(0)).getChildAt(0).getTop());
        assertFalse(list.getViewTreeObserver().dispatchOnPreDraw());
        contract.detached(list);
    }
    @Test public void contentBearingPageDecorationAndReplacementKeepHeaderWithoutMovingControls() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=templateCard(context);list.addView(card);
        var page=(FrameLayout)((FrameLayout)card.i).getChildAt(0);
        var original=new android.graphics.drawable.GradientDrawable();page.setForeground(original);
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.beforeDraw(card,true);
        assertTrue(page.getForeground() instanceof CardHeaderDrawable);
        assertSame(original,((CardHeaderDrawable)page.getForeground()).nativeDrawable);
        assertSame(page.getForeground(),original.getCallback());
        assertEquals(0,page.getTop());assertEquals(0,page.getChildAt(1).getTop());
        var replacement=new android.graphics.drawable.GradientDrawable();page.setForeground(replacement);
        contract.beforeDraw(card,true);assertSame(replacement,((CardHeaderDrawable)page.getForeground()).nativeDrawable);
        assertSame(page.getForeground(),replacement.getCallback());
        contract.restore();assertSame(replacement,page.getForeground());
    }
    @Test public void genericDecorationDetectionDoesNotStretchButtonsOrPartialAndCustomDrawnViews() throws Exception {
        var context=Robolectric.buildActivity(Activity.class).setup().get();var list=new Container(context);
        var card=templateCard(context);list.addView(card);var page=(FrameLayout)((FrameLayout)card.i).getChildAt(0);
        var button=new FrameLayout(context);button.setBackgroundColor(0xff123456);button.setClickable(true);
        page.addView(button,new FrameLayout.LayoutParams(-1,-1));
        var partial=new FrameLayout(context);partial.setBackgroundColor(0xff123456);
        page.addView(partial,new FrameLayout.LayoutParams(-1,200));
        var custom=new View(context);custom.setBackgroundColor(0xff123456);page.addView(custom,new FrameLayout.LayoutParams(-1,-1));
        var drawn=new CustomDrawnFrame(context);drawn.setBackgroundColor(0xff123456);page.addView(drawn,new FrameLayout.LayoutParams(-1,-1));
        var contract=contract(false);list.setTranslationY(200);contract.afterNativeLayout(list,true);
        card.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        card.layout(0,14,1000,14+card.getMeasuredHeight());contract.afterLayout(card,true);
        assertEquals(0,button.getTop());assertEquals(350,button.getHeight());
        assertEquals(0,partial.getTop());assertEquals(200,partial.getHeight());
        assertEquals(0,custom.getTop());assertEquals(350,custom.getHeight());
        assertEquals(0,drawn.getTop());assertEquals(350,drawn.getHeight());
    }
    private static NativeCameraCard.Plan plan(float y,boolean portrait) {
        return NativeCameraCard.plan(new Rect(460,25,540,115),new Rect(461,0,539,141),1000,150,y,14,portrait,true);
    }
    private static NativeCameraCard contract(boolean old) throws Exception {
        return new NativeCameraCard(lookup(old),(view,y)->plan(y,true));
    }
    private static int margin(View view) { return ((FrameLayout.LayoutParams)view.getLayoutParams()).topMargin; }
    private static Card card(Context context,int top,int height) {
        var card=new Card(context);var child=new View(context);var params=new FrameLayout.LayoutParams(-1,height);
        params.topMargin=top;card.addView(child,params);card.i=child;card.s=child;return card;
    }
    private static NativeCameraCard.Lookup lookup(boolean old) {
        var types=Map.of(NativeCameraCard.VIEWS+"CardContainer",Container.class,NativeCameraCard.VIEWS+"CardView",Card.class,
                old?"i5.d":NativeCameraCard.VIEWS+"e",Root.class,
                old?"l5.h":"com.oplus.systemui.plugins.seedling.card.window.i",Window.class,
                "com.oplus.systemui.plugins.shared.template.component.media.view.MediaBackGroundView",MediaBackground.class,
                NativeCameraCard.VIEWS+"CardBackgroundView",BlurBackground.class,
                old?"j6.h":"com.oplus.systemui.plugins.common.util.j",OutlineProvider.class);
        return name->{
            if(name.equals(NativeCameraCard.VIEWS+"InstantContainer"))return Instant.class;
            if(name.equals("h5.n")||name.equals("com.oplus.systemui.plugins.seedling.card.ui.model.n"))return InstantModel.class;
            var type=types.get(name);if(type==null)throw new ClassNotFoundException(name);return type;
        };
    }
    public static class Root extends FrameLayout {
        public final Rect h=new Rect(),s=h;public Root(Context c){super(c);}
        public int getCardStartLocation(){return 70;}public Container getCardContainer(){return null;}
        public void d(){}public void e(){}
    }
    public static class Container extends FrameLayout {
        int adapterOverride=-2,layoutOverride=-2;
        boolean failPositions;
        public Container(Context c){super(c);}public void k(){}public void i(){}
        public List<Integer> getItemViewsCenter(){return List.of();}
        public int getChildAdapterPosition(View view){if(failPositions)throw new IllegalStateException("SDK position contract lost");return adapterOverride==-2?indexOfChild(view):adapterOverride;}
        public int getChildLayoutPosition(View view){return layoutOverride==-2?indexOfChild(view):layoutOverride;}
        @Override public void onDetachedFromWindow(){super.onDetachedFromWindow();}
    }
    public static class Card extends FrameLayout {
        public View i,s;public Card(Context c){super(c);}
        private int getIntrinsicHeight(){return i==null?getHeight():i.getHeight();}
        public void setLeftTopRightBottomForBlur(Rect rect){}
    }
    public static class HighlightCard extends Card {
        boolean highlight; public HighlightCard(Context c){super(c);}
        @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
            super.dispatchDraw(canvas);
            if(highlight)canvas.drawColor(0x30ffffff);
        }
    }
    public static class InstantModel { }
    public static class Instant extends FrameLayout {
        View current;boolean expanded;int positionWrites;
        public Instant(Context c){super(c);}
        public void k(InstantModel state,boolean keep,boolean hide){}
        public void s(){positionWrites++;if(current!=null){var params=new FrameLayout.LayoutParams(1000,-2);params.topMargin=200;current.setLayoutParams(params);}}
        public void q(){s();}
        public View getInstantView(){return current;}public boolean l(){return expanded;}
        public static android.graphics.Region region(Instant container) {
            View view=container.current;
            if(view==null)return new android.graphics.Region();
            // This fixture is detached and its container starts at window 0,0.
            return new android.graphics.Region(view.getLeft(),view.getTop(),view.getRight(),view.getBottom());
        }
        public static void p(View view,Rect rect,float fraction,int from,int to){}
        public static void o(View view,Rect rect,float fraction,int from,int to){}
        @Override protected void dispatchDraw(android.graphics.Canvas canvas){super.dispatchDraw(canvas);}
        @Override public void onDetachedFromWindow(){super.onDetachedFromWindow();}
    }
    public static class MediaBackground extends View { public MediaBackground(Context c){super(c);} }
    public static class CustomDrawnFrame extends FrameLayout {
        public CustomDrawnFrame(Context c){super(c);}
        @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);}
    }
    public static class BlurBackground extends View {
        float blurAlpha=1;public BlurBackground(Context c){super(c);}
        public void setBlurRadiusFollowAlpha(float value){blurAlpha=value;}
    }
    public static class SurfaceCanvas extends android.graphics.Canvas {
        int color;@Override public void drawColor(int value){color=value;}
    }
    public static class OutlineProvider extends android.view.ViewOutlineProvider {
        public final Rect a,c;public OutlineProvider(Rect rect){a=rect;c=rect;}
        @Override public void getOutline(View view,android.graphics.Outline outline){outline.setRect(a);}
    }
    public static class Window { public Root f,g;public void a(boolean back){} }
}
