package dev.lsposed.fluidcloud;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWaveSpectrumTest {
    @Test public void sixMusicFrequenciesDriveOnlyTheirCorrespondingLeftToRightColumn(){
        int[] bins={2,5,12,30,70,200};
        for(int rate:new int[]{44_100_000,48_000_000})for(int band=0;band<6;band++){
            byte[] fft=new byte[1024];fft[2*bins[band]]=50;fft[2*bins[band]+1]=-20;
            byte[] before=fft.clone();var levels=new MediaWaveSpectrum(rate).read(fft,100);
            for(int i=0;i<6;i++)if(i==band)assertTrue(levels.at(i)>.6f);else assertEquals(0,levels.at(i),0);
            assertArrayEquals(before,fft);
        }
    }
    @Test public void dcNyquistAndEmptyBinQuantizationCannotPretendToBeMusic(){
        var spectrum=new MediaWaveSpectrum(48_000_000);byte[] fft=new byte[1024];
        assertEquals(MediaWaveLevels.SILENT,spectrum.read(fft,100));
        Arrays.fill(fft,(byte)1);fft[0]=127;fft[1]=-128;
        assertEquals(MediaWaveLevels.SILENT,spectrum.read(fft,200));
    }
    @Test public void signedRealAndImaginaryPartsHaveTheSameEnergy(){
        var spectrum=new MediaWaveSpectrum(48_000_000);byte[] a=new byte[1024],b=new byte[1024];
        a[24]=80;b[25]=-80;assertEquals(spectrum.read(a,100),spectrum.read(b,100));
        a[24]=-128;assertEquals(1,spectrum.read(a,100).at(2),0);
    }
    @Test public void simultaneousBassAndTrebleDoNotForceOtherBandsToMove(){
        byte[] fft=new byte[1024];fft[4]=40;fft[400]=30;var levels=new MediaWaveSpectrum(48_000_000).read(fft,100);
        assertTrue(levels.a()>levels.f());assertTrue(levels.f()>0);for(int i=1;i<5;i++)assertEquals(0,levels.at(i),0);
    }
    @Test public void frequencyEdgesBelongToOneBandAndRespectActualSamplingRateUnits(){
        byte[] fft=new byte[1024];fft[8]=50; // Bin 4 is exactly 125 Hz at 32 kHz.
        var levels=new MediaWaveSpectrum(32_000_000).read(fft,100);assertEquals(0,levels.a(),0);assertTrue(levels.b()>0);
        fft=new byte[1024];fft[2]=100; // 31.25 Hz lies below the chosen music range.
        assertEquals(MediaWaveLevels.SILENT,new MediaWaveSpectrum(32_000_000).read(fft,100));
    }
    @Test public void moreEnergyAndUserSensitivityIncreaseTheSameFrequencyBand(){
        var spectrum=new MediaWaveSpectrum(48_000_000);byte[] fft=new byte[1024];fft[60]=20;
        float soft=spectrum.read(fft,100).d();fft[60]=40;float loud=spectrum.read(fft,100).d();
        assertTrue(loud>soft);assertTrue(spectrum.read(fft,200).d()>loud);assertTrue(loud<1);
    }
    @Test public void aCustomBoundaryMovesARealBinToTheAdjacentBand() {
        byte[] fft = new byte[1024];
        fft[8] = 50; // 125 Hz at 32 kHz.
        var original = new MediaWaveSpectrum(32_000_000).read(fft, 100);
        var custom = new MediaWaveSpectrum(32_000_000,
                MediaWaveBands.parse("40,200,315,800,2000,5000,16000")).read(fft, 100);
        assertTrue(original.b() > 0);
        assertEquals(original.b(), custom.a(), 0);
        assertEquals(0, original.a(), 0);
        for (int band = 1; band < MediaWaveLevels.COUNT; band++) assertEquals(0, custom.at(band), 0);
    }
    @Test public void narrowBandsAndBandsBeyondNyquistStayQuietWithoutLosingTheOtherBands() {
        var spectrum = new MediaWaveSpectrum(32_000_000,
                MediaWaveBands.parse("20,21,125,315,800,16000,24000"));
        byte[] fft = new byte[1024];
        fft[1] = 127; // Packed Nyquist value is excluded.
        fft[2] = 50; // The first ordinary bin, 31.25 Hz, is in the second band.
        fft[1022] = 50; // The last ordinary bin remains in the fifth band.
        var levels = spectrum.read(fft, 100);
        assertEquals(0, levels.a(), 0);
        assertTrue(levels.b() > 0);
        assertTrue(levels.e() > 0);
        assertEquals(0, levels.f(), 0);
    }
    @Test public void unknownOrUnresolvableRatesAndWrongCaptureSizeFailClosed(){
        for(int rate:new int[]{0,-1,48_000,192_000_000})try{new MediaWaveSpectrum(rate);fail();}catch(IllegalArgumentException expected){ }
        try{new MediaWaveSpectrum(48_000_000).read(new byte[256],100);fail();}catch(IllegalArgumentException expected){ }
    }
}
