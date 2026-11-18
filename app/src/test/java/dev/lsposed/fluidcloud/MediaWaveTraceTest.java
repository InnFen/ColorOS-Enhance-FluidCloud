package dev.lsposed.fluidcloud;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWaveTraceTest {
    @Test public void reportSeparatesRepeatedInputAndMissingDrawsWithoutLoggingSpectrumValues(){
        var rows=new ArrayList<String>();var trace=new MediaWaveTrace(rows::add);
        trace.sample(100,MediaWaveLevels.SILENT);trace.frame(101);trace.draw(102,false);
        trace.sample(200,MediaWaveLevels.SILENT);trace.sample(2100,MediaWaveLevels.SILENT);
        assertEquals(1,rows.size());assertTrue(rows.getFirst().contains("samples=3 changed=1 frames=1 draws=1"));
        assertTrue(rows.getFirst().contains("sampleGap=1900"));
        assertFalse(rows.getFirst().contains("0.0"));
    }
}
