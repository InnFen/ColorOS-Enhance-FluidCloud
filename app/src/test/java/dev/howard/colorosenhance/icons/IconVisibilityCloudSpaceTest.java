package dev.howard.colorosenhance.icons;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class IconVisibilityCloudSpaceTest {
    private final Map<String,Object> prefs=new HashMap<>(Map.of("enabled",true,"shade_only.network_speed",true));
    private final IconVisibility adapter=new IconVisibility(()->StatusIconConfig.from(prefs));
    private final Context context=RuntimeEnvironment.getApplication();
    private final LinearLayout row=new LinearLayout(context);
    private final View left=new View(context),right=new View(context);
    private final NativeIcon speed=new NativeIcon(context);

    private void setup(String location,boolean rtl)throws Exception{
        row.addView(left);row.addView(speed);row.addView(right);
        left.layout(0,0,20,20);speed.layout(20,0,48,20);right.layout(48,0,68,20);
        row.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        adapter.group(row,location);
        adapter.icon(speed,"network_speed",IconReflection.method(NativeIcon.class,"isIconVisible"),IconReflection.method(NativeIcon.class,"isIconBlocked"));
        adapter.seamless(true);
    }
    private IconDrawPlan.Transform draw(View view){
        var frame=adapter.begin(row);try{return adapter.transform(row,view);}finally{adapter.end(frame);}
    }
    @Test public void capsuleOcclusionCannotReintroduceTheReservedCellWhileContentRemainsMasked()throws Exception{
        setup("HOME",false);
        // Native updateStates allocates all eligible cells first. Capsule occlusion only
        // changes visibleState, leaving those translations and widths intact.
        for(int state:new int[]{0,2,2,0,2,0}){
            speed.state=state;speed.setAlpha(.65f);
            var transform=draw(left);
            assertEquals("state="+state,right.getX(),left.getX()+left.getWidth()+transform.dx(),0);
            assertEquals(0,draw(speed).alpha(),0);
            assertEquals(.65f,speed.getAlpha(),0);assertEquals(20,speed.getX(),0);
        }
    }
    @Test public void stateChangesDuringEitherPanelGesturePreserveContinuousSpacingAndFade()throws Exception{
        for(String location:List.of("HOME","QS")){
            if(row.getChildCount()==0)setup(location,false);else adapter.group(row,location);
            for(float fraction:new float[]{0,.2f,.7f,1,.7f,.2f,0}){
                adapter.fraction(fraction);
                for(int state:new int[]{0,2}){
                    speed.state=state;
                    float gap=right.getX()-(left.getX()+left.getWidth()+draw(left).dx());
                    assertEquals(location+" state="+state,28*fraction,gap,.0001f);
                    assertEquals(fraction,draw(speed).alpha(),0);
                }
            }
        }
    }
    @Test public void rtlAndNativePaddingStillRemoveExactlyTheAllocatedSpace()throws Exception{
        setup("HOME",true);speed.state=2;
        left.layout(48,0,68,20);speed.layout(20,0,48,20);right.layout(0,0,20,20);
        assertEquals(right.getX()+right.getWidth(),left.getX()+draw(left).dx(),0);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        left.layout(0,0,20,20);speed.layout(20,0,48,20);
        speed.setPadding(2,0,4,0);right.layout(54,0,74,20);
        assertEquals(right.getX(),left.getX()+left.getWidth()+draw(left).dx(),0);
    }
    @Test public void actualNativeExclusionDoesNotCompactAnAlreadyRemovedCellTwice()throws Exception{
        setup("HOME",false);speed.state=2;speed.nativeVisible=false;right.layout(20,0,40,20);
        assertEquals(IconDrawPlan.Transform.NONE,draw(left));
        speed.nativeVisible=true;speed.blocked=true;assertEquals(IconDrawPlan.Transform.NONE,draw(left));
        speed.blocked=false;adapter.group(row,"HOME",new NativeIconPolicy(List.of(java.util.Set.of("network_speed")),true));
        assertEquals(IconDrawPlan.Transform.NONE,draw(left));
    }
    @Test public void independentNativeMirrorUsesTheSameGapCompensationWithoutLeakingDrawScope()throws Exception{
        setup("HOME",false);speed.state=2;
        assertEquals(28,adapter.mirrored(left).dx(),0);
        assertEquals(IconDrawPlan.Transform.NONE,adapter.transform(row,left));
        prefs.put("enabled",false);assertEquals(IconDrawPlan.Transform.NONE,adapter.mirrored(left));
    }
    @Test public void adjacentSelectionsKeepOneCompensationForEachCellAcrossOcclusionThresholds()throws Exception{
        setup("HOME",false);prefs.put("shade_only.wifi",true);
        var wifi=new NativeIcon(context);row.addView(wifi,2);wifi.layout(48,0,66,20);right.layout(66,0,86,20);
        adapter.icon(wifi,"wifi",IconReflection.method(NativeIcon.class,"isIconVisible"),IconReflection.method(NativeIcon.class,"isIconBlocked"));
        for(int speedState:new int[]{0,2})for(int wifiState:new int[]{0,2}){
            speed.state=speedState;wifi.state=wifiState;
            assertEquals(46,draw(left).dx(),0);assertEquals(0,draw(speed).alpha(),0);assertEquals(0,draw(wifi).alpha(),0);
        }
    }
    @Test public void vpnInsertionCannotDropCompactionWhenNativeTranslationsCrossOrCoincide()throws Exception{
        setup("HOME",false);
        for(float fraction:new float[]{0,.4f,1}){
            adapter.fraction(fraction);
            for(float shift:new float[]{0,-12,-28,-40,20,0}){
                speed.setTranslationX(shift);
                assertEquals(28*(1-fraction),draw(left).dx(),.0001f);
                assertEquals(0,draw(right).dx(),0);
            }
        }
    }
    @Test public void nativeZeroLeftLayoutUsesSlotOrderRatherThanAnAnimationStartingPosition()throws Exception{
        setup("HOME",false);
        left.layout(0,0,20,20);speed.layout(0,0,28,20);right.layout(0,0,20,20);
        for(float x:new float[]{0,20,-10,40}){
            speed.setTranslationX(x);left.setTranslationX(20-x);
            assertEquals(28,draw(left).dx(),0);assertEquals(0,draw(right).dx(),0);
        }
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        assertEquals(-28,draw(left).dx(),0);assertEquals(0,draw(right).dx(),0);
    }
    @Test public void nativeSlotRemovalAndRecreationKeepVisibleNeighborInPlace()throws Exception{
        setup("HOME",false);row.layout(0,0,68,20);
        left.layout(0,0,20,20);speed.layout(20,0,48,20);right.layout(48,0,68,20);
        assertEquals(28,draw(left).dx(),0);row.removeView(speed);
        for(float x:new float[]{0,9,20,28}){
            left.setX(x);assertEquals(28,left.getX()+draw(left).dx(),.001f);
        }
        row.addView(speed,1);speed.layout(20,0,48,20);
        for(float x:new float[]{28,19,7,0}){
            left.setX(x);assertEquals(28,left.getX()+draw(left).dx(),.001f);
        }
    }
    public static class NativeIcon extends View {
        int state;boolean nativeVisible=true,blocked;
        NativeIcon(Context context){super(context);}
        public boolean isIconVisible(){return nativeVisible;}
        public boolean isIconBlocked(){return blocked;}
        public int getVisibleState(){return state;}
    }
}
