package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;
import dev.lsposed.fluidcloud.PipelineVisibilityPolicy.*;

public class PipelineVisibilityPolicyTest {
    private static final Flags HIDDEN=new Flags(false,false,false), SHOWN=new Flags(true,true,true);
    private static final Base BASE=new Base(true,false,HIDDEN);
    private static final Capsule CAPSULE=new Capsule(true,false,false,HIDDEN);
    @Test public void completeSafeUnknownHideIsOtherButIncompleteAndProtectedInputsAreNative() {
        Decision other=PipelineVisibilityPolicy.classify(0x20000,0,BASE,CAPSULE,4);
        assertEquals(CloudScene.OTHER,other.scene());assertTrue(other.entering());
        assertNull(PipelineVisibilityPolicy.classify(-1,-1,BASE,CAPSULE,4).scene());
        assertTrue(PipelineVisibilityPolicy.classify(0,0,BASE,
                new Capsule(true,false,true,HIDDEN),4).protectedInput());
    }
    @Test public void otherRequiresAFreshVisibleToHiddenTransitionOrAnOwnedDuplicate() {
        assertNull(PipelineVisibilityPolicy.classifyTransition(0x20000,0,BASE,CAPSULE,
                4,-1,null).scene());
        assertEquals(CloudScene.OTHER,PipelineVisibilityPolicy.classifyTransition(0x20000,0,
                BASE,CAPSULE,4,0,null).scene());
        assertEquals(CloudScene.OTHER,PipelineVisibilityPolicy.classifyTransition(0x20000,0,
                BASE,CAPSULE,4,4,CloudScene.OTHER).scene());
    }

}
