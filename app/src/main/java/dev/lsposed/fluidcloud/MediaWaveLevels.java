package dev.lsposed.fluidcloud;

/** A tiny immutable handoff; no PCM crosses to the UI thread. */
record MediaWaveLevels(float a, float b, float c, float d, float e, float f) {
    static final int COUNT = 6;
    static final MediaWaveLevels SILENT = new MediaWaveLevels(0,0,0,0,0,0);
    float at(int index) {
        return switch(index) { case 0 -> a; case 1 -> b; case 2 -> c; case 3 -> d; case 4 -> e; case 5 -> f;
            default -> throw new IndexOutOfBoundsException(index); };
    }
    static float display(double amplitude,int sensitivity){
        // Gate before applying user gain, then retain the 1.21.3 soft display curve.
        int gain = Math.max(MediaWaveOptions.SENSITIVITY_MIN_PERCENT,
                Math.min(MediaWaveOptions.SENSITIVITY_MAX_PERCENT, sensitivity));
        double energy = Math.max(0, Math.min(1, (amplitude - .015) / .985 * gain / 100d));
        return (float)(energy/(.4+.6*energy));
    }
}
