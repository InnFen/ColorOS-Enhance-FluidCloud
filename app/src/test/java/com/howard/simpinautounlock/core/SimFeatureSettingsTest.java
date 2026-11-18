package com.howard.simpinautounlock.core;

import static org.junit.Assert.*;
import java.util.Map;
import org.junit.Test;

public final class SimFeatureSettingsTest {
    @Test public void missingAndUnreadableSettingsStayOff() {
        assertFalse(SimFeatureSettings.read(Map::of).enabled());
        assertTrue(SimFeatureSettings.read(Map::of).suppressPinFlash());
        assertFalse(SimFeatureSettings.read(() -> { throw new IllegalStateException(); }).enabled());
        assertFalse(SimFeatureSettings.read(() -> null).enabled());
    }
    @Test public void explicitOptInUsesTrueSuppressionDefault() {
        SimFeatureSettings settings = SimFeatureSettings.read(() -> Map.of("enabled", true));
        assertTrue(settings.enabled());
        assertTrue(settings.suppressPinFlash());
    }
    @Test public void independentSuppressionSwitchDoesNotDisablePinSupply() {
        SimFeatureSettings settings = SimFeatureSettings.read(() ->
                Map.of("enabled", true, "suppress_pin_flash", false));
        assertTrue(settings.enabled());
        assertFalse(settings.suppressPinFlash());
    }
    @Test public void malformedSettingFailsClosed() {
        assertFalse(SimFeatureSettings.read(() -> Map.of("enabled", "true")).enabled());
        assertFalse(SimFeatureSettings.read(() ->
                Map.of("enabled", true, "suppress_pin_flash", "true")).enabled());
    }
}
