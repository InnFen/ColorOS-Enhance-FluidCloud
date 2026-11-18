package com.howard.simpinautounlock.hook;

import static org.junit.Assert.assertEquals;

import java.util.OptionalInt;
import org.junit.Test;

public final class PinResultEventResolverTest {
    @Test
    public void resolvesEventWhenRomInlinesTheConstantField() {
        OptionalInt event = PinResultEventResolver.resolve(InlinedEventProfile.class);

        assertEquals(16, event.orElse(-1));
    }

    @Test
    public void preservesOlderRomConstantFieldFallback() {
        OptionalInt event = PinResultEventResolver.resolve(FieldEventProfile.class);

        assertEquals(9, event.orElse(-1));
    }

    private static final class InlinedEventProfile {
        private static String eventToString(int event) {
            return event == 16 ? "SUPPLY_ICC_PIN_DONE" : "UNKNOWN(" + event + ")";
        }
    }

    private static final class FieldEventProfile {
        private static final int EVENT_SUPPLY_ICC_PIN_DONE = 9;
    }
}
