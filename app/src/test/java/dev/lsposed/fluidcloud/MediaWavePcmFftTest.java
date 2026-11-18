package dev.lsposed.fluidcloud;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class MediaWavePcmFftTest {
    static byte[] tone(int bin, double amplitude) {
        byte[] pcm = new byte[MediaWaveSpectrum.SIZE];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (byte) Math.round(128 + amplitude * Math.cos(2 * Math.PI * bin * i / pcm.length));
        return pcm;
    }
    @Test public void sixTonesLandInTheirOwnFrequencyBands() {
        var transform = new MediaWavePcmFft(); var spectrum = new MediaWaveSpectrum(48_000_000);
        int[] bins = {2, 4, 10, 25, 64, 160}; byte[] fft = new byte[1024];
        for (int band = 0; band < bins.length; band++) {
            transform.transform(tone(bins[band], 20), fft);
            var levels = spectrum.read(fft, 100);
            for (int other = 0; other < 6; other++) {
                if (other == band) assertTrue("Selected frequency", levels.at(other) > .6);
                else assertEquals("Other frequency", 0, levels.at(other), .025);
            }
        }
    }
    @Test public void silenceAndDcProduceNoMusicEnergyAndReuseDoesNotRetainPreviousBins() {
        var transform = new MediaWavePcmFft(); var spectrum = new MediaWaveSpectrum(48_000_000);
        byte[] fft = new byte[1024], pcm = new byte[1024];
        transform.transform(tone(25, 20), fft); assertNotEquals(MediaWaveLevels.SILENT, spectrum.read(fft, 100));
        Arrays.fill(pcm, (byte) 128); transform.transform(pcm, fft);
        assertEquals(MediaWaveLevels.SILENT, spectrum.read(fft, 100));
        Arrays.fill(pcm, (byte) 180); transform.transform(pcm, fft);
        assertEquals(MediaWaveLevels.SILENT, spectrum.read(fft, 100));
    }
    @Test public void matchesPackedVisualizerScaleForAnExactCosineBin() {
        var transform = new MediaWavePcmFft(); byte[] fft = new byte[1024];
        transform.transform(tone(64, 10), fft);
        assertEquals(40, fft[128], 2); assertEquals(0, fft[129], 1);
    }
}
