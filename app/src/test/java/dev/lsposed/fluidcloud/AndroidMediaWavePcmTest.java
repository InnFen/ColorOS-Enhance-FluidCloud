package dev.lsposed.fluidcloud;

import android.app.Application;
import android.media.audiofx.AudioEffect;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36,application=Application.class,shadows=AndroidMediaWavePcmTest.Effect.class)
public class AndroidMediaWavePcmTest {
    @Test public void aPrivateEngineAdvertisedBeforeItIsReadyUsesTheBoundedRetryPath() throws Exception {
        Effect.reset(); Effect.notReady=true;
        try { AndroidMediaWavePcm.open(12,48000,()->MediaWaveOptions.DEFAULTS,null); fail(); }
        catch(MediaWaveCapture.NotReady expected){assertEquals(0,Effect.sets);assertEquals(0,Effect.enables);}
    }
    @Test public void solePrivateEffectUsesThePlatformNullUuidAndReleasesAfterSuccessfulCapture() throws Exception {
        Effect.reset();
        var source = AndroidMediaWavePcm.open(12,48000,()->MediaWaveOptions.DEFAULTS,null);
        assertNotNull(source); assertFalse(Effect.released); assertTrue(Effect.enabled); assertEquals(0,Effect.latency);
        source.read(); assertEquals(2,Effect.captures);
        source.close(); assertTrue(Effect.released); assertFalse(Effect.enabled); assertEquals(400,Effect.latency);
    }
    @Test public void unsupportedLatencyReleasesWithoutChangingTheOldEnginesSettings() throws Exception {
        Effect.reset(); Effect.supported=false;
        assertNull(AndroidMediaWavePcm.open(12,48000,()->MediaWaveOptions.DEFAULTS,null));
        assertTrue(Effect.released); assertEquals(0,Effect.sets); assertEquals(0,Effect.enables);
    }
    @Test public void failedConfigurationDoesNotLeakThePrivateEffect() throws Exception {
        Effect.reset(); Effect.refuseSize=true;
        try { AndroidMediaWavePcm.open(12,48000,()->MediaWaveOptions.DEFAULTS,null); fail(); }
        catch(IllegalStateException expected){assertTrue(Effect.released);assertFalse(Effect.enabled);}
    }
    @Test public void sessionZeroAndUnknownMixerRateNeverCreateAnEffect() throws Exception {
        Effect.reset();
        for(int[] pair:new int[][]{{0,48000},{12,0}}){
            try{AndroidMediaWavePcm.open(pair[0],pair[1],()->MediaWaveOptions.DEFAULTS,null);fail();}
            catch(IllegalArgumentException expected){assertEquals(0,Effect.creations);}
        }
    }
    @Implements(AudioEffect.class)
    public static class Effect {
        static boolean enabled,released,supported,refuseSize,notReady;
        static int latency,captures,sets,enables,creations;
        static void reset(){enabled=released=refuseSize=notReady=false;supported=true;latency=400;captures=sets=enables=creations=0;}
        @Implementation protected void __constructor__(UUID type,UUID implementation,int priority,int session){
            assertEquals(UUID.fromString("e46b26a0-dddd-11db-8afd-0002a5d5c51b"),type);
            assertEquals(UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210"),implementation);
            assertEquals(0,priority);assertTrue(session>0);creations++;
            if(notReady)throw new RuntimeException("Cannot initialize effect engine for type: "+type+" Error: -3");
        }
        @Implementation protected boolean hasControl(){return true;}
        @Implementation protected boolean getEnabled(){return enabled;}
        @Implementation protected int setEnabled(boolean value){enables++;enabled=value;return AudioEffect.SUCCESS;}
        @Implementation protected int getParameter(int parameter,int[] value){assertEquals(2,parameter);value[0]=latency;return supported?1:AudioEffect.ERROR_BAD_VALUE;}
        @Implementation protected int setParameter(int parameter,int value){sets++;if(parameter==2)latency=value;return refuseSize&&parameter==0?AudioEffect.ERROR_BAD_VALUE:AudioEffect.SUCCESS;}
        @Implementation protected int command(int command,byte[] input,byte[] pcm){
            assertEquals(0x10000,command);assertEquals(0,input.length);captures++;
            System.arraycopy(MediaWavePcmFftTest.tone(4,20),0,pcm,0,pcm.length);return pcm.length;
        }
        @Implementation protected void release(){released=true;}
    }
}
