package dev.howard.colorosenhance.icons;

import org.junit.Test;
import static org.junit.Assert.*;

public class IconReflectionTest {
    @Test public void pipelineInterfaceDefaultsAndClassOverridesRetainNativeBlocking() throws Exception {
        var inherited = new PipelineIcon();
        var blocked = new BlockedIcon();
        assertEquals(false, IconReflection.method(inherited.getClass(), "isIconBlocked").invoke(inherited));
        assertEquals(true, IconReflection.method(blocked.getClass(), "isIconBlocked").invoke(blocked));
        assertNull(IconReflection.optional(PipelineIcon.class, "missingNativeMethod"));
    }
    interface Displayable { default boolean isIconBlocked() { return false; } }
    static class PipelineIcon implements Displayable {}
    static class BlockedIcon extends PipelineIcon { @Override public boolean isIconBlocked() { return true; } }
}
