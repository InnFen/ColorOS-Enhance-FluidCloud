package dev.howard.colorosenhance.icons;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShadeProgressTest {
    @Test public void controlCenterGestureWorksWhileNotificationFractionStaysZero() {
        var progress=new ShadeProgress();
        assertEquals(.4f,progress.controls(.4f),0);assertEquals(.4f,progress.notification(0),0);
        assertEquals(1,progress.controls(1),0);assertEquals(.2f,progress.controls(.2f),0);assertEquals(0,progress.controls(0),0);
    }
    @Test public void switchingPanelsDoesNotBrieflyHideIconsWhileEitherIsExpanded() {
        var progress=new ShadeProgress();progress.notification(1);
        assertEquals(1,progress.controls(.5f),0);assertEquals(.5f,progress.notification(0),0);
        assertEquals(1,progress.controls(1),0);assertEquals(0,progress.controls(0),0);
    }
    @Test public void boundedAndInvalidEventsDoNotCorruptTheOtherPanelProgress() {
        var progress=new ShadeProgress();assertEquals(1,progress.notification(2),0);
        assertEquals(1,progress.notification(Float.NaN),0);assertEquals(1,progress.controls(Float.POSITIVE_INFINITY),0);
        assertEquals(0,progress.notification(-1),0);
    }
}
