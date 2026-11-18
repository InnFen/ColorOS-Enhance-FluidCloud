package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class CapsuleTokenFadeTest {
    @Test public void eitherModuleWindowLayerIsEligible() {
        assertTrue(CapsuleTokenFade.matches(2026,"com.android.systemui","ColorOSEnhance-Cloud"));
        assertTrue(CapsuleTokenFade.matches(2017,"com.android.systemui","ColorOSEnhance-Cloud"));
        assertFalse(CapsuleTokenFade.matches(2038,"com.android.systemui","ColorOSEnhance-Cloud"));
        assertFalse(CapsuleTokenFade.matches(2017,"com.android.systemui","SeedlingCard"));
        assertFalse(CapsuleTokenFade.matches(2026,"com.android.settings","ColorOSEnhance-Cloud"));
        assertFalse(CapsuleTokenFade.matches(2026,"com.android.systemui","NotificationShade"));
        assertFalse(CapsuleTokenFade.matches(2026,"com.android.systemui",null));
    }

    @Test public void differentAndUnspecifiedRotationsKeepNativeBehavior() {
        assertTrue(CapsuleTokenFade.sameRotation(0,0));
        assertTrue(CapsuleTokenFade.sameRotation(1,1));
        assertFalse(CapsuleTokenFade.sameRotation(1,0));
        assertFalse(CapsuleTokenFade.sameRotation(-1,-1));
    }
}
