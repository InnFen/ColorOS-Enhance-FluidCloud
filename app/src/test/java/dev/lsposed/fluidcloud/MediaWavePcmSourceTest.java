package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWavePcmSourceTest {
    @Test public void releaseRestoresTheNativeLatencyBeforeTheSameEngineIsReused() throws Exception {
        var engine=new Engine();
        try(var source=new MediaWavePcmSource(engine,()->MediaWaveOptions.DEFAULTS,()->0)){
            assertEquals(0,engine.latency);source.read();
        }
        assertEquals(400,engine.latency);assertTrue(engine.closed);engine.closed=false;
        try(var source=new MediaWavePcmSource(engine,()->MediaWaveOptions.DEFAULTS,()->0)){
            assertEquals(0,engine.latency);source.read();
        }
        assertEquals(400,engine.latency);
    }
    @Test public void anEmptyNewEngineWindowDoesNotReplaceTheRetainedFrameDuringBluetoothWarmup() throws Exception {
        var engine=new Engine();engine.initialEmpty=true;long[] now={0};
        try(var source=new MediaWavePcmSource(engine,()->MediaWaveOptions.DEFAULTS,()->now[0])){
            source.read();assertTrue(source.buffering());now[0]=399;source.read();assertTrue(source.buffering());
            now[0]=400;assertTrue(source.read().b()>.6);assertFalse(source.buffering());
        }
    }
    @Test public void zeroAndShortLatenciesUseTheSameCapturePathWithoutBluetoothAssumptions() throws Exception {
        for(int latency:new int[]{0,10,30,80,200,700,1500,3000}){
            var engine=new Engine();engine.initialEmpty=true;engine.latency=latency;long[] now={0};
            try(var source=new MediaWavePcmSource(engine,()->MediaWaveOptions.DEFAULTS,()->now[0])){
                source.read();assertEquals(latency!=0,source.buffering());
                now[0]=latency;assertTrue(source.read().b()>.6);assertFalse(source.buffering());
            }
        }
    }
    @Test public void disablingDiagnosticsStopsAggregationWithoutReopeningCapture() throws Exception {
        var engine=new Engine();long[] now={0};boolean[] enabled={true};var messages=new java.util.ArrayList<String>();
        try(var source=new MediaWavePcmSource(engine,()->MediaWaveOptions.DEFAULTS,()->now[0],messages::add,()->enabled[0])){
            source.read();now[0]=2000;source.read();assertEquals(1,messages.size());
            enabled[0]=false;now[0]=4000;source.read();now[0]=6000;source.read();assertEquals(1,messages.size());
            assertEquals(5,engine.captures);assertFalse(engine.closed);
        }
    }
    static final class Engine implements MediaWavePcmSource.Engine {
        int latency = 400, bin = 4, captures, zeroed; boolean closed,initialEmpty;
        public int samplingRateHz() { return 48000; }
        public int latencyMs() { return latency; }
        public void latencyMs(int value) { latency = value; zeroed++; }
        public void capture(byte[] pcm) { captures++; if(initialEmpty&&captures==1){java.util.Arrays.fill(pcm,(byte)128);return;} System.arraycopy(MediaWavePcmFftTest.tone(bin, 20), 0, pcm, 0, pcm.length); }
        public void close() { closed = true; }
    }
    @Test public void freshWindowRetainsAudibleLatencyUsingOnlyDelayedHeights() throws Exception {
        var engine = new Engine(); long[] now = {0};
        try (var source = new MediaWavePcmSource(engine, () -> MediaWaveOptions.DEFAULTS, () -> now[0])) {
            assertEquals(0, engine.latency); assertEquals(1, engine.zeroed);
            engine.bin = 25; var first = source.read(); assertTrue(first.b() > .6); assertEquals(0, first.d(), .025);
            now[0] = 399; assertEquals(first, source.read());
            now[0] = 400; var next = source.read(); assertTrue(next.d() > .6); assertEquals(0, next.b(), .025);
        }
        assertTrue(engine.closed);
    }
    @Test public void ringBoundaryDoesNotChangeTheFreshCaptureWindowAndQueueIsBounded() throws Exception {
        var engine = new Engine(); engine.latency = 3000; long[] now = {0};
        try (var source = new MediaWavePcmSource(engine, () -> MediaWaveOptions.DEFAULTS, () -> now[0])) {
            for (int i = 0; i < 500; i++) { now[0] = i * 20L; engine.bin = i % 2 == 0 ? 4 : 25; source.read(); }
            assertEquals(1, engine.zeroed); assertEquals(501, engine.captures);
        }
    }
    @Test public void reconfigurationClearsOldTimelineAndRestoresFreshWindow() throws Exception {
        var engine = new Engine(); long[] now = {0};
        try (var source = new MediaWavePcmSource(engine, () -> MediaWaveOptions.DEFAULTS, () -> now[0])) {
            source.read(); now[0] = 400; engine.latency = 100; engine.bin = 25;
            assertTrue(source.read().b() > .6); assertEquals(0, engine.latency); assertEquals(2, engine.zeroed);
            now[0] = 500; assertTrue(source.read().d() > .6);
        }
    }
}
