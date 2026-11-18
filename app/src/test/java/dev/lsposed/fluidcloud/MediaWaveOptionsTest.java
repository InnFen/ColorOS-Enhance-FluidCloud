package dev.lsposed.fluidcloud;
import java.util.Map;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaWaveOptionsTest {
    @Test public void upgradesAndMalformedValuesUseSafeDefaults(){
        assertEquals(MediaWaveOptions.DEFAULTS,MediaWaveOptions.from(Map.of()));
        assertEquals(MediaWaveOptions.DEFAULTS,MediaWaveOptions.from(Map.of("wave_interval_ms","40","wave_sensitivity",true,"wave_hold_paused","true")));
    }
    @Test public void boundsAndStepsAreEnforcedEvenForOutOfBandPreferenceWrites(){
        var value = new MediaWaveOptions(-1, Integer.MAX_VALUE, 7, 9999, true);
        assertEquals(20, value.intervalMs());
        assertEquals(500, value.sensitivityPercent());
        assertEquals(5, value.attackMs());
        assertEquals(2000, value.releaseMs());
        assertTrue(value.holdPaused());
        var opposite = new MediaWaveOptions(997, 1, 999, 12, false);
        assertEquals(1000, opposite.intervalMs());
        assertEquals(5, opposite.sensitivityPercent());
        assertEquals(1000, opposite.attackMs());
        assertEquals(10, opposite.releaseMs());
    }
    @Test public void waveChoicesRemainSharedAcrossOrientationAndIndependentOfSceneUnbind(){
        var config = CloudConfig.from(Map.of("enabled", true, "realtime_media_wave", true, "wave_interval_ms", 120,
                "wave_sensitivity", 150, "wave_hold_paused", true, MediaWaveBands.KEY, "40,200,315,800,2000,5000,16000"));
        assertEquals(120,config.extras().waveOptions().intervalMs());assertTrue(config.extras().waveOptions().holdPaused());
        assertEquals(200, config.extras().waveOptions().bands().edgeHz(1));
        assertEquals(config.extras().waveOptions(),config.forOrientation(true).extras().waveOptions());assertFalse(config.scenesEnabled());
    }

    @Test public void missingOrMalformedBandsKeepTheOriginalBoundaries() {
        assertEquals("40,125,315,800,2000,5000,16000", MediaWaveOptions.DEFAULTS.bands().encode());
        for (String invalid : List.of("", "40,125,315,800,2000,5000", "40,125,315,800,2000,5000,16000,",
                "40,125,125,800,2000,5000,16000", "40,315,125,800,2000,5000,16000",
                "19,125,315,800,2000,5000,16000", "40,125,315,800,2000,5000,24001",
                "40,125,315,800,2000,5000,16k", "40,125,315,800,2000,5000,16000.0")) {
            assertNull(invalid, MediaWaveBands.parse(invalid));
            var settings = MediaWaveOptions.from(Map.of(MediaWaveBands.KEY, invalid, "wave_interval_ms", 200));
            assertEquals(MediaWaveBands.DEFAULTS, settings.bands());
            assertEquals(200, settings.intervalMs());
        }
        assertEquals(MediaWaveBands.DEFAULTS, MediaWaveOptions.from(Map.of(MediaWaveBands.KEY, true)).bands());
        var custom = MediaWaveBands.parse(" 20, 200, 315, 800, 2000, 5000, 24000 ");
        assertEquals("20,200,315,800,2000,5000,24000", custom.encode());
        assertEquals(custom, MediaWaveBands.parse(custom.encode()));
    }
}
