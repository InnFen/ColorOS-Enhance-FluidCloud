package dev.howard.colorosenhance.icons;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class IconOrderPlanTest {
    private final IconOrderPlan.Bounds bounds=new IconOrderPlan.Bounds(20,450,550,980);
    @Test public void clockBatteryAndSystemIconsCanInterleaveAcrossBothPhysicalSides(){
        var order=new IconOrder(List.of("wifi","battery","clock"),List.of("mobile","vpn"));
        var cells=List.of(cell(0,"clock",70,false),cell(1,"wifi",30,false),cell(2,"mobile",40,false),cell(3,"battery",50,false),cell(4,"vpn",20,false));
        var plan=IconOrderPlan.create(order,cells,bounds);
        assertEquals(20,plan.get(1).left(),0);assertEquals(50,plan.get(3).left(),0);assertEquals(100,plan.get(0).left(),0);
        assertEquals(920,plan.get(2).left(),0);assertEquals(960,plan.get(4).left(),0);
    }
    @Test public void dualSimItemsStayAdjacentAndKeepNativeSubscriptionOrder(){
        var plan=IconOrderPlan.create(new IconOrder(List.of("mobile"),List.of("wifi")),
                List.of(cell(0,"mobile",40,false),cell(1,"mobile",45,false),cell(2,"wifi",30,false)),bounds);
        assertEquals(20,plan.get(0).left(),0);assertEquals(60,plan.get(1).left(),0);assertEquals(950,plan.get(2).left(),0);
    }
    @Test public void shadeOnlySpaceCompactsTowardTheChosenOuterEdgeRegardlessOfNativeParent(){
        var plan=IconOrderPlan.create(new IconOrder(List.of("wifi","clock"),List.of("battery","mobile")),
                List.of(cell(0,"wifi",30,true),cell(1,"clock",70,false),cell(2,"battery",50,false),cell(3,"mobile",40,true)),bounds);
        assertEquals(-30,plan.get(1).compactDx(),0);assertEquals(40,plan.get(2).compactDx(),0);
        assertEquals(0,plan.get(0).compactDx(),0);assertEquals(0,plan.get(3).compactDx(),0);
    }
    @Test public void overflowNeverPlacesIconsAcrossTheCameraAndAvailableOuterItemsRemainVisible(){
        var tiny=new IconOrderPlan.Bounds(20,80,920,980);
        var plan=IconOrderPlan.create(new IconOrder(List.of("clock","wifi"),List.of("mobile","battery")),
                List.of(cell(0,"clock",70,false),cell(1,"wifi",30,false),cell(2,"mobile",40,false),cell(3,"battery",50,false)),tiny);
        assertFalse(plan.containsKey(0));assertTrue(plan.containsKey(1));assertFalse(plan.containsKey(2));assertTrue(plan.containsKey(3));
        for(var position:plan.values())assertTrue(position.left()>=920||position.left()<80);
    }
    @Test public void malformedMeasurementsCannotEmitNonFiniteCoordinates(){
        assertTrue(IconOrderPlan.create(IconOrder.EMPTY,List.of(cell(0,"wifi",20,false)),new IconOrderPlan.Bounds(0,Float.NaN,500,1000)).isEmpty());
        var plan=IconOrderPlan.create(IconOrder.EMPTY,List.of(cell(0,"wifi",Float.POSITIVE_INFINITY,false),cell(1,"battery",50,false)),bounds);
        assertFalse(plan.containsKey(0));assertEquals(930,plan.get(1).left(),0);
    }
    @Test public void shadeOnlySlotsDoNotEvictIconsThatFitInTheCollapsedStatusBar(){
        var plan=IconOrderPlan.create(new IconOrder(List.of(),List.of("clock","battery","wifi")),
                List.of(cell(0,"clock",40,false),cell(1,"battery",60,false),cell(2,"wifi",60,true)),new IconOrderPlan.Bounds(0,400,800,900));
        assertEquals(3,plan.size());
        assertEquals(800,plan.get(0).left()+plan.get(0).compactDx(),0);
        assertEquals(840,plan.get(1).left()+plan.get(1).compactDx(),0);
    }
    private IconOrderPlan.Cell cell(int id,String slot,float width,boolean selected){return new IconOrderPlan.Cell(id,slot,width,selected);}
}
