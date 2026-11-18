package dev.howard.colorosenhance.icons;

import android.app.Application;
import android.view.View;
import android.widget.LinearLayout;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class IconSpaceContinuityTest {
    private final LinearLayout row=new LinearLayout(RuntimeEnvironment.getApplication());
    private final View left=new View(row.getContext()),hidden=new View(row.getContext()),right=new View(row.getContext());
    private final IconSpaceContinuity spacing=new IconSpaceContinuity();
    private void setup(){
        row.addView(left);row.addView(hidden);row.addView(right);row.layout(0,0,210,20);
        left.layout(0,0,63,20);hidden.layout(0,0,77,20);right.layout(0,0,70,20);
        left.setX(0);hidden.setX(63);right.setX(140);
    }
    private List<IconDrawPlan.Transform> apply(float progress){
        var cells=row.getChildCount()==3?List.of(new IconDrawPlan.Cell(0,63,false),new IconDrawPlan.Cell(1,77,true),new IconDrawPlan.Cell(2,70,false))
                :List.of(new IconDrawPlan.Cell(0,63,false),new IconDrawPlan.Cell(1,70,false));
        return spacing.apply(row,cells,row.getChildCount()==3?List.of(true,true,true):List.of(true,true),true,progress,
                IconDrawPlan.at(cells,true,progress),()->{});
    }
    @Test public void hiddenSlotRecreationCannotApplyItsFullWidthBeforeNativeNeighborsHaveMoved(){
        setup();
        // After the parent's resize, the native left icon is still at its old global
        // position. Its animation travels from x=77 to x=0 as the suppressed slot arrives.
        for(float x:new float[]{77,62,40,12,0}){
            left.setX(x);assertEquals(77,left.getX()+apply(0).getFirst().dx(),.0001f);
            assertEquals(0,apply(0).get(1).alpha(),0);
        }
    }
    @Test public void removalKeepsCompensatingTheOldCellUntilTheNativeAnimationClosesItsGap(){
        setup();apply(0);row.removeView(hidden);
        for(float x:new float[]{0,5,27,50,77}){
            left.setX(x);assertEquals(77,left.getX()+apply(0).getFirst().dx(),.0001f);
        }
        assertEquals(IconDrawPlan.Transform.NONE,apply(0).getFirst());
    }
    @Test public void partialShadeProgressRevealsOnlyTheSpaceActuallyAvailableInTheNativeAnimation(){
        setup();
        for(float x:new float[]{77,40,0})for(float fraction:new float[]{0,.25f,.8f,1}){
            left.setX(x);assertEquals((77-x)*(1-fraction),apply(fraction).getFirst().dx(),.0001f);
            assertEquals(fraction,apply(fraction).get(1).alpha(),0);
        }
    }
    @Test public void aNewUnselectedNeighborDoesNotBecomePartOfTheRemovedSlotsGap(){
        setup();apply(0);row.removeView(hidden);
        View vpn=new View(row.getContext());row.addView(vpn,1);vpn.layout(0,0,77,20);vpn.setX(63);
        var cells=List.of(new IconDrawPlan.Cell(0,63,false),new IconDrawPlan.Cell(1,77,false),new IconDrawPlan.Cell(2,70,false));
        var plan=spacing.apply(row,cells,List.of(true,true,true),true,0,IconDrawPlan.at(cells,true,0),()->{});
        assertTrue(plan.stream().allMatch(IconDrawPlan.Transform.NONE::equals));
    }
    @Test public void trailingHiddenSlotUsesTheNativeEndEdgeAndRtlUsesTheOppositeGap(){
        setup();row.removeView(right);
        var cells=List.of(new IconDrawPlan.Cell(0,63,false),new IconDrawPlan.Cell(1,77,true));
        row.layout(0,0,140,20);left.setX(40);
        assertEquals(77,left.getX()+spacing.apply(row,cells,List.of(true,true),true,0,IconDrawPlan.at(cells,true,0),()->{}).getFirst().dx(),0);
        row.removeView(hidden);row.addView(right);row.addView(hidden,1);right.setX(0);left.setX(90);
        cells=List.of(new IconDrawPlan.Cell(0,63,false),new IconDrawPlan.Cell(-1,77,true),new IconDrawPlan.Cell(-2,70,false));
        var rtl=new IconSpaceContinuity();
        assertEquals(-20,rtl.apply(row,cells,List.of(true,true,true),false,0,IconDrawPlan.at(cells,false,0),()->{}).getFirst().dx(),0);
    }
}
