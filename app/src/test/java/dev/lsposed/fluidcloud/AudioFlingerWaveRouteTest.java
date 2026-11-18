package dev.lsposed.fluidcloud;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioFlingerWaveRouteTest {
    @Test public void captureRateBelongsToTheVerifiedOutputNotTheResampledTrackOrAnotherThread(){
        String rateHeader=HEADER.replace("  1 Tracks", " Sample rate: 48000 Hz\n  1 Tracks");
        assertEquals(48000,AudioFlingerWaveRoute.assess(rateHeader+ROW,10352,3673).samplingRateHz());
        assertEquals(0,AudioFlingerWaveRoute.assess(HEADER+ROW,10352,3673).samplingRateHz());
        assertEquals(0,AudioFlingerWaveRoute.assess(rateHeader.replace("48000","invalid")+ROW,10352,3673).samplingRateHz());
        assertEquals(0,AudioFlingerWaveRoute.assess(rateHeader.replace("MIXER","OFFLOAD")+ROW,10352,3673).samplingRateHz());
        String other=rateHeader.replace("48000","96000").replace("AudioOut_15","AudioOut_20");
        assertEquals(48000,AudioFlingerWaveRoute.assess(other+ROW.replace("3673","3674")+rateHeader+ROW,10352,3673).samplingRateHz());
    }
    private static final String STREAM=" AudioStreamOut: 0x456 flags 0x8 (AUDIO_OUTPUT_FLAG_DEEP_BUFFER)\n";
    private static final String HEADER="Output thread 0x123, name AudioOut_15, tid 10, type 0 (MIXER):\n"+STREAM+"  1 Tracks of which 1 are active\n"
            +" Type Id Active Client(pid/uid) Session Port Id S Flags Format Chn mask SRate ST Usg CT G db L dB R dB VS dB PortVol dB PortMuted Server FrmCnt FrmRdy F Underruns Flushed BitPerfect InternalMute Latency\n";
    private static final String ROW=" 1245 yes 100/ 10352 3673 1311 A 0x000 00000001 00000003 44100 3 1 2 0 0 0 0 0 false 00000000 2058 0 f 0 0 false false 10\n";
    @Test public void onlyAPositiveLivePrivateOrdinaryPcmTrackIsAllowed(){
        assertTrue(AudioFlingerWaveRoute.safe(HEADER+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW,10351,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW,10352,0));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW.replace("yes","no"),10352,3673));
    }
    @Test public void fillingStateColumnDoesNotChangeTheBitPerfectOrRouteSafetyDecision(){
        for(String state:new String[]{"A","F","f"})
            assertTrue(state,AudioFlingerWaveRoute.safe(HEADER+ROW.replace(" 0 f 0 0 false"," 0 "+state+" 0 0 false"),10352,3673));
    }
    @Test public void resumingOrdinaryMixerTrackKeepsTheSameSafetyConstraints(){
        String resuming=ROW.replace("1311 A ","1311 r ");
        assertTrue(AudioFlingerWaveRoute.safe(HEADER+resuming,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER.replace("MIXER","OFFLOAD")+resuming,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+resuming.replace("0x000","0x004"),10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+resuming.replace("yes","no"),10352,3673));
        for(String state:new String[]{"p","P","S","I","T","x"})
            assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW.replace("1311 A ","1311 "+state+" "),10352,3673));
    }
    @Test public void offloadDirectMmapAndSpatializedOrUnknownThreadsAreRejectedBeforeCreatingEffects(){
        for(String type:new String[]{"OFFLOAD","DIRECT","MMAP_PLAYBACK","SPATIALIZER","VENDOR","DUPLICATING"})
            assertFalse(type,AudioFlingerWaveRoute.safe(HEADER.replace("MIXER",type)+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe("Tracks\n"+ROW,10352,3673));
    }
    @Test public void fastRawDirectAndUnrecognizedOutputFlagsNeverBecomeABestGuess(){
        for(String flags:new String[]{"0x4","0x1","0x10","0x100","0x4000","0x100000","0xdeadbeef"})
            assertEquals(flags,MediaWaveCapture.Route.UNSAFE,AudioFlingerWaveRoute.inspect(HEADER.replace("flags 0x8","flags "+flags)+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW.replace("00000001","08000000"),10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW.replace("false false 10","true false 10"),10352,3673));
    }
    @Test public void duplicatesHistoryAndInputTracksCannotPassAsACurrentPlaybackRoute(){
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+" Local log:\n"+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+" 0 Effect Chains\n"+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe("Input thread test\n"+HEADER.substring(HEADER.indexOf("  1 Tracks"))+ROW,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(null,10352,3673));
        assertFalse(AudioFlingerWaveRoute.safe(HEADER+ROW.replace("false false 10",""),10352,3673));
    }
    @Test public void staleInactiveTrackDoesNotRejectItsLiveReplacementInTheSamePrivateSession(){
        String old=ROW.replace("1245 yes","1244 no").replace("1311 A","1310 S");
        assertTrue(AudioFlingerWaveRoute.safe(HEADER+old+ROW,10352,3673));
        assertTrue(AudioFlingerWaveRoute.safe(HEADER+ROW+old,10352,3673));
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+old,10352,3673));
    }
    @Test public void knownUnsafeOutputsAreSeparatedFromAStartupSnapshotThatIsNotReady(){
        assertEquals(MediaWaveCapture.Route.UNSAFE,AudioFlingerWaveRoute.inspect(HEADER.replace("MIXER","OFFLOAD")+ROW,10352,3673));
        assertEquals(MediaWaveCapture.Route.UNSAFE,AudioFlingerWaveRoute.inspect(HEADER.replace("flags 0x8","flags 0x4")+ROW,10352,3673));
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+ROW.replace("0x000","0x004"),10352,3673));
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(null,10352,3673));
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER,10352,3673));
    }
    @Test public void currentBilibiliUnderrunIsAControlBlockStatusNotAnUnsafeOutputRoute(){
        String row="5702 yes 19248/ 10350 16841 6118 A 0x001 00000001 00000003 44100 3 1 0 -1.5 0 0 0 -1.5 false 0016AE80 26390 26390 A 1881 0 false false 1241.78 t 0 1 4 0 0\n";
        var assessment=AudioFlingerWaveRoute.assess(HEADER+row,10350,16841);
        assertEquals(MediaWaveCapture.Route.SAFE,assessment.route());
        assertEquals("ordinary PCM mixer output=0x8 cblk=0x1",assessment.reason());
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.assess(HEADER+row.replace("0x001","0x004"),10350,16841).route());
    }
    @Test public void diagnosticsExplainRouteRejectionWithoutIncludingClientIdentity(){
        assertEquals("output=OFFLOAD",AudioFlingerWaveRoute.assess(HEADER.replace("MIXER","OFFLOAD")+ROW,10352,3673).reason());
        assertEquals("output flags=0x4",AudioFlingerWaveRoute.assess(HEADER.replace("flags 0x8","flags 0x4")+ROW,10352,3673).reason());
        assertEquals("cblk not ready=0x4",AudioFlingerWaveRoute.assess(HEADER+ROW.replace("0x000","0x004"),10352,3673).reason());
        assertEquals("bit-perfect track",AudioFlingerWaveRoute.assess(HEADER+ROW.replace("false false 10","true false 10"),10352,3673).reason());
    }
    @Test public void onlyNonePrimaryAndDeepBufferOutputFlagsOrTheirCombinationAreAllowed(){
        for(String flags:new String[]{"0","0x0","0x2","0x8","0xa"})
            assertTrue(flags,AudioFlingerWaveRoute.safe(HEADER.replace("flags 0x8","flags "+flags)+ROW,10352,3673));
    }
    @Test public void absentMalformedNullOrRepeatedOutputStreamFieldsFailClosed(){
        for(String stream:new String[]{"",STREAM.replace("flags 0x8","flags unknown"),STREAM.replace("0x456","(nil)"),
                STREAM+STREAM,STREAM+STREAM.replace("flags 0x8","flags 0x2")})
            assertEquals(stream,MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER.replace(STREAM,stream)+ROW,10352,3673));
    }
    @Test public void outputFlagsBelongOnlyToTheirOwnThreadAndAreCheckedAfterTheWholeBlock(){
        String missing=HEADER.replace(STREAM,"");
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+missing+ROW,10352,3673));
        assertTrue(AudioFlingerWaveRoute.safe(HEADER.replace("flags 0x8","flags 0x1")+HEADER+ROW,10352,3673));
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+ROW+STREAM.replace("flags 0x8","flags 0x2"),10352,3673));
        assertTrue(AudioFlingerWaveRoute.safe(missing+ROW+STREAM,10352,3673));
    }
    @Test public void knownHarmlessControlBlockFlagsDoNotChangeAnOrdinaryMixerRoute(){
        for(String flags:new String[]{"0x000","0x001","0x002","0x003","0x020","0x023"})
            assertTrue(flags,AudioFlingerWaveRoute.safe(HEADER+ROW.replace("0x000",flags),10352,3673));
    }
    @Test public void invalidDisabledEndingInterruptedOrUnknownControlBlockFlagsWaitForAReadyTrack(){
        for(String flags:new String[]{"0x004","0x008","0x040","0x080","0x100","0x200","0x400","0x010","0x800","0xdeadbeef"})
            assertEquals(flags,MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+ROW.replace("0x000",flags),10352,3673));
    }
    @Test public void fastTrackPrefixIsRejectedEvenBesideAnOrdinaryTrackInTheSameSession(){
        String fast=" F1 "+ROW.trim()+"\n";
        for(String rows:new String[]{fast,ROW+fast,fast+ROW," F12 S "+ROW.trim()+"\n"})
            assertEquals(rows,MediaWaveCapture.Route.UNSAFE,AudioFlingerWaveRoute.inspect(HEADER+rows,10352,3673));
        assertTrue(AudioFlingerWaveRoute.safe(HEADER+fast.replace("10352","10351")+ROW,10352,3673));
        assertTrue(AudioFlingerWaveRoute.safe(HEADER+fast.replace("yes","no")+ROW,10352,3673));
    }
    @Test public void specialOrUnrecognizedTrackTypesAreNeverTreatedAsOrdinaryDynamicTracks(){
        for(String type:new String[]{"S","P","?","Vendor"})
            assertEquals(type,MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+" "+type+" "+ROW,10352,3673));
    }
    @Test public void malformedSelectedActiveTrackCannotBeIgnoredBesideAValidTrack(){
        String invalid=ROW.replace("1311 A 0x000 00000001","1311 A unknown 00000001");
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+invalid+ROW,10352,3673));
        assertEquals(MediaWaveCapture.Route.WAIT,AudioFlingerWaveRoute.inspect(HEADER+ROW+invalid,10352,3673));
    }
}
