package dev.lsposed.fluidcloud;

/** Reusable radix-2 transform for the private Visualizer's unsigned 8-bit PCM. */
final class MediaWavePcmFft {
    private static final int N = MediaWaveSpectrum.SIZE;
    private final double[] real = new double[N], imaginary = new double[N];
    private final double[] cosine = new double[N / 2], sine = new double[N / 2];
    private final int[] reversed = new int[N];

    MediaWavePcmFft() {
        for (int i = 0; i < N; i++) reversed[i] = Integer.reverse(i) >>> (32 - 10);
        for (int i = 0; i < N / 2; i++) {
            cosine[i] = Math.cos(-2 * Math.PI * i / N);
            sine[i] = Math.sin(-2 * Math.PI * i / N);
        }
    }

    void transform(byte[] pcm, byte[] fft) {
        if (pcm.length != N || fft.length != N) throw new IllegalArgumentException("Capture size changed");
        for (int i = 0; i < N; i++) {
            real[reversed[i]] = (pcm[i] & 255) - 128;
            imaginary[i] = 0;
        }
        for (int width = 2; width <= N; width *= 2) {
            int half = width / 2, stride = N / width;
            for (int start = 0; start < N; start += width) {
                for (int k = 0; k < half; k++) {
                    int a = start + k, b = a + half, phase = k * stride;
                    double r = real[b] * cosine[phase] - imaginary[b] * sine[phase];
                    double im = real[b] * sine[phase] + imaginary[b] * cosine[phase];
                    real[b] = real[a] - r; imaginary[b] = imaginary[a] - im;
                    real[a] += r; imaginary[a] += im;
                }
            }
        }
        // Match Visualizer's packed signed-byte representation and component scaling.
        // Keep the established band mapping, quantization gate and sensitivity curve.
        fft[0] = component(real[0]); fft[1] = component(real[N / 2]);
        for (int k = 1; k < N / 2; k++) {
            fft[2 * k] = component(real[k]); fft[2 * k + 1] = component(imaginary[k]);
        }
    }

    private static byte component(double value) {
        int scaled = (int) Math.floor(value * 8 / N + 1e-9);
        while (scaled > 127 || scaled < -128) scaled >>= 1;
        return (byte) scaled;
    }
}
