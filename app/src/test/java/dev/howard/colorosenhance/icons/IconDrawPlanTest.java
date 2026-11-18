package dev.howard.colorosenhance.icons;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class IconDrawPlanTest {
    @Test public void revealedAreaFitsTheSpaceOpenedByNeighborMotionOnEitherSide() {
        assertEquals(new IconDrawPlan.Reveal(30,30),IconDrawPlan.reveal(10,20,true,0));
        assertEquals(new IconDrawPlan.Reveal(20,30),IconDrawPlan.reveal(10,20,true,.5f));
        assertEquals(new IconDrawPlan.Reveal(10,30),IconDrawPlan.reveal(10,20,true,1));
        assertEquals(new IconDrawPlan.Reveal(10,20),IconDrawPlan.reveal(10,20,false,.5f));
        assertEquals(new IconDrawPlan.Reveal(10,10),IconDrawPlan.reveal(10,20,false,Float.NaN));
    }
    private final List<IconDrawPlan.Cell> cells=List.of(new IconDrawPlan.Cell(0,20,false),
            new IconDrawPlan.Cell(20,30,true),new IconDrawPlan.Cell(50,20,false));
    @Test public void hiddenIconAndItsLeftNeighborFollowTheSameGestureProgress() {
        var collapsed=IconDrawPlan.at(cells,true,0);assertEquals(30,collapsed.get(0).dx(),0);assertEquals(0,collapsed.get(1).alpha(),0);
        var partial=IconDrawPlan.at(cells,true,.4f);assertEquals(18,partial.get(0).dx(),.001);assertEquals(.4f,partial.get(1).alpha(),0);
        assertEquals(IconDrawPlan.Transform.NONE,partial.get(2));
        var expanded=IconDrawPlan.at(cells,true,1);assertTrue(expanded.stream().allMatch(IconDrawPlan.Transform.NONE::equals));
        assertEquals(0,cells.get(0).left(),0);assertEquals(30,cells.get(1).width(),0); // Native geometry is unchanged.
    }
    @Test public void reversingACancelledGestureUsesCurrentProgressWithoutAnAnimator() {
        var first=IconDrawPlan.at(cells,true,.3f);IconDrawPlan.at(cells,true,.9f);
        assertEquals(first,IconDrawPlan.at(cells,true,.3f));
        assertEquals(30,IconDrawPlan.at(cells,true,0).get(0).dx(),0);
    }
    @Test public void rtlAndStartSideClockCompactionUseTheOppositeSide() {
        var left=IconDrawPlan.at(cells,false,0);assertEquals(-30,left.get(2).dx(),0);assertEquals(0,left.get(0).dx(),0);
        assertEquals(-15,IconDrawPlan.at(cells,false,.5f).get(2).dx(),0);
    }
    @Test public void severalSelectedIconsDoNotLeaveGapsWhenCollapsed() {
        var plan=IconDrawPlan.at(List.of(new IconDrawPlan.Cell(0,10,false),new IconDrawPlan.Cell(10,20,true),new IconDrawPlan.Cell(30,25,true)),true,0);
        assertEquals(45,plan.get(0).dx(),0);assertEquals(25,plan.get(1).dx(),0);assertEquals(0,plan.get(2).alpha(),0);
    }
    @Test public void boundsAndStockExcludedItemsDoNotCreateFalseSpaceOrOpacity() {
        assertEquals(IconDrawPlan.at(cells,true,0),IconDrawPlan.at(cells,true,-1));
        assertEquals(IconDrawPlan.at(cells,true,1),IconDrawPlan.at(cells,true,2));
        var none=IconDrawPlan.at(List.of(new IconDrawPlan.Cell(0,20,false),new IconDrawPlan.Cell(20,30,false)),true,.3f);
        assertTrue(none.stream().allMatch(IconDrawPlan.Transform.NONE::equals));
    }
    @Test public void permanentlyHiddenLockscreenIconsStayHiddenWhileShadeOnlyIconsReveal() {
        var mixed=List.of(new IconDrawPlan.Cell(0,20,false),new IconDrawPlan.Cell(20,30,true),new IconDrawPlan.Cell(50,15,true,true));
        var partial=IconDrawPlan.at(mixed,true,.5f);assertEquals(30,partial.get(0).dx(),0);
        assertEquals(.5f,partial.get(1).alpha(),0);assertEquals(0,partial.get(2).alpha(),0);
        var expanded=IconDrawPlan.at(mixed,true,1);assertEquals(15,expanded.get(0).dx(),0);
        assertEquals(1,expanded.get(1).alpha(),0);assertEquals(0,expanded.get(2).alpha(),0);
    }
}
