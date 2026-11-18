package dev.lsposed.fluidcloud;

/** Six bands over Visualizer-format 1024-point FFT data; all bin work stays on the capture worker. */
final class MediaWaveSpectrum {
    static final int SIZE=1024;
    private final int[] boundaries = new int[MediaWaveBands.EDGE_COUNT];

    MediaWaveSpectrum(int samplingRateMilliHz) {
        this(samplingRateMilliHz, MediaWaveBands.DEFAULTS);
    }

    MediaWaveSpectrum(int samplingRateMilliHz, MediaWaveBands bands) {
        if (samplingRateMilliHz <= 0) throw new IllegalArgumentException("Unknown sampling rate");
        double binWidthHz = samplingRateMilliHz / (1000d * SIZE);
        setBoundaries(MediaWaveBands.DEFAULTS, binWidthHz);
        // Keep the existing sampling-rate contract. On a supported rate, an empty
        // custom band simply has zero energy and can be adjusted during playback.
        for (int band = 0; band < MediaWaveLevels.COUNT; band++) {
            if (boundaries[band] == boundaries[band + 1]) {
                throw new IllegalArgumentException("Insufficient spectral resolution");
            }
        }
        if (!MediaWaveBands.DEFAULTS.equals(bands)) setBoundaries(bands, binWidthHz);
    }

    private void setBoundaries(MediaWaveBands bands, double binWidthHz) {
        for (int i = 0; i < boundaries.length; i++) {
            int bin = (int) Math.ceil(bands.edgeHz(i) / binWidthHz);
            boundaries[i] = Math.max(1, Math.min(SIZE / 2, bin));
        }
    }
    String binSummary() {
        return java.util.Arrays.toString(boundaries);
    }
    MediaWaveLevels read(byte[] fft,int sensitivity){
        if(fft.length!=SIZE)throw new IllegalArgumentException("FFT size changed");
        return new MediaWaveLevels(band(fft,0,sensitivity),band(fft,1,sensitivity),band(fft,2,sensitivity),
                band(fft,3,sensitivity),band(fft,4,sensitivity),band(fft,5,sensitivity));
    }
    private float band(byte[] fft,int band,int sensitivity){
        double power=0;
        // FFT bytes are signed complex values, unlike unsigned waveform PCM. Index 0
        // is DC and index 1 is Nyquist; neither is part of the six music bands.
        for(int k=boundaries[band];k<boundaries[band+1];k++){
            int real=fft[2*k],imaginary=fft[2*k+1];int binPower=real*real+imaginary*imaginary;
            if(binPower>2)power+=binPower; // Ignore one-bit quantization in empty bins.
        }
        return MediaWaveLevels.display(Math.sqrt(power)/128d,sensitivity);
    }
}
