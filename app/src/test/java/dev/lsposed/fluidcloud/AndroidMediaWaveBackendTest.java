package dev.lsposed.fluidcloud;

import android.app.Application;
import android.media.audiofx.Visualizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,application=Application.class,shadows=AndroidMediaWaveBackendTest.Engine.class)
public class AndroidMediaWaveBackendTest {
    @Test public void inheritedEnabledEngineIsStoppedBeforeConfiguringItsCaptureSize()throws Exception{
        var visualizer=new Visualizer(12);Engine engine=Shadow.extract(visualizer);engine.enabled=true;
        var source=AndroidMediaWaveBackend.configure(visualizer,()->MediaWaveOptions.DEFAULTS);
        assertEquals(List.of("disable","size","scaling","enable"),engine.events);
        assertTrue(engine.enabled);source.close();assertFalse(engine.enabled);assertTrue(engine.released);
    }
    @Test public void actualSamplingRateCanSettleAfterActivationWithoutDiscardingTheSource()throws Exception{
        var visualizer=new Visualizer(12);Engine engine=Shadow.extract(visualizer);
        var source=AndroidMediaWaveBackend.configure(visualizer,()->MediaWaveOptions.DEFAULTS);
        assertNotNull(source.read());engine.rate=48000000;assertNotNull(source.read());
        assertEquals(2,engine.reads);assertFalse(engine.released);source.close();
    }
    @Test public void changingAndRestoringTheBoundariesMovesBinsWithoutReopeningTheEffect() throws Exception {
        var visualizer = new Visualizer(12);
        Engine engine = Shadow.extract(visualizer);
        engine.rate = 32_000_000;
        engine.signalBin = 4; // 125 Hz moves from the second band to the first.
        var settings = new AtomicReference<>(MediaWaveOptions.DEFAULTS);
        try (var source = AndroidMediaWaveBackend.configure(visualizer, settings::get)) {
            var original = source.read();
            assertTrue(original.b() > 0);
            settings.set(MediaWaveOptions.from(Map.of(MediaWaveBands.KEY, "40,200,315,800,2000,5000,16000")));
            var custom = source.read();
            assertEquals(original.b(), custom.a(), 0);
            assertEquals(0, custom.b(), 0);
            settings.set(MediaWaveOptions.DEFAULTS);
            assertEquals(original, source.read());
            assertEquals(3, engine.reads);
            assertEquals(List.of("disable", "size", "scaling", "enable"), engine.events);
            assertTrue(engine.enabled);
            assertFalse(engine.released);
        }
    }
    @Test public void failedConfigurationReleasesThePrivateHandle()throws Exception{
        var visualizer=new Visualizer(12);Engine engine=Shadow.extract(visualizer);engine.refuseSize=true;
        try{AndroidMediaWaveBackend.configure(visualizer,()->MediaWaveOptions.DEFAULTS);fail();}
        catch(IllegalStateException expected){assertTrue(engine.released);assertFalse(engine.enabled);}
    }
    @Test public void invalidRateAndFailedReadsAreNotReportedAsValidSpectrum()throws Exception{
        var visualizer=new Visualizer(12);Engine engine=Shadow.extract(visualizer);
        var source=AndroidMediaWaveBackend.configure(visualizer,()->MediaWaveOptions.DEFAULTS);
        engine.rate=0;try{source.read();fail();}catch(MediaWaveCapture.NotReady expected){ }
        engine.rate=44100000;engine.readable=false;
        try{source.read();fail();}catch(MediaWaveCapture.NotReady expected){ }finally{source.close();}
        assertTrue(engine.released);
    }
    @Test public void disabledVisualizerIsNotMisreportedAsMeasuredSilentAudio()throws Exception{
        var visualizer=new Visualizer(12);Engine engine=Shadow.extract(visualizer);
        var source=AndroidMediaWaveBackend.configure(visualizer,()->MediaWaveOptions.DEFAULTS);engine.enabled=false;
        try{source.read();fail();}catch(MediaWaveCapture.NotReady expected){assertEquals(0,engine.reads);}finally{source.close();}
    }
    @Test public void temporaryEngineConfigurationStatusIsRetryableAndReleasesTheHandle()throws Exception{
        var visualizer=new Visualizer(12);Engine engine=Shadow.extract(visualizer);engine.sizeStatus=Visualizer.ERROR_INVALID_OPERATION;
        try{AndroidMediaWaveBackend.configure(visualizer,()->MediaWaveOptions.DEFAULTS);fail();}
        catch(MediaWaveCapture.NotReady expected){assertTrue(engine.released);assertFalse(engine.enabled);}
    }
    @Implements(Visualizer.class)
    public static class Engine {
        boolean enabled,released,refuseSize,readable=true;int rate=44100000,reads,sizeStatus=Visualizer.SUCCESS,signalBin;
        final List<String> events=new ArrayList<>();
        @Implementation protected void __constructor__(int session){assertTrue(session>0);}
        @Implementation protected static int[] getCaptureSizeRange(){return new int[]{128,1024};}
        @Implementation protected int setEnabled(boolean value){enabled=value;events.add(value?"enable":"disable");return Visualizer.SUCCESS;}
        @Implementation protected int setCaptureSize(int value){
            if(enabled)throw new IllegalStateException("Native API requires a disabled engine");
            events.add("size");return refuseSize?Visualizer.ERROR:sizeStatus;
        }
        @Implementation protected int setScalingMode(int mode){events.add("scaling");return Visualizer.SUCCESS;}
        @Implementation protected int getSamplingRate(){return rate;}
        @Implementation protected boolean getEnabled(){return enabled;}
        @Implementation protected int getFft(byte[] buffer) {
            reads++;
            if (signalBin > 0) buffer[2 * signalBin] = 50;
            return readable ? Visualizer.SUCCESS : Visualizer.ERROR;
        }
        @Implementation protected void release(){released=true;}
    }
}
