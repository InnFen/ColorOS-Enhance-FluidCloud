package dev.lsposed.fluidcloud;

import android.media.audiofx.AudioEffect;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One privately owned Visualizer effect, using capability-checked platform commands. */
final class AndroidMediaWavePcm {
    private static final UUID TYPE = UUID.fromString("e46b26a0-dddd-11db-8afd-0002a5d5c51b");
    private static final UUID ANY = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210"); // EFFECT_TYPE_NULL
    private AndroidMediaWavePcm() { }

    static MediaWaveCapture.Source open(int session, int rate, Supplier<MediaWaveOptions> options,
                                        Consumer<String> diagnostic) throws Exception {
        if (session <= 0) throw new IllegalArgumentException("Private session required");
        if (rate < 32000 || rate > 192000) throw new IllegalArgumentException("Verified mixer rate required");
        Method set = Reflect.method(AudioEffect.class, "setParameter", int.class, int.class, int.class);
        Method get = Reflect.method(AudioEffect.class, "getParameter", int.class, int.class, int[].class);
        Method command = Reflect.method(AudioEffect.class, "command", int.class, int.class, byte[].class, byte[].class);
        var constructor = AudioEffect.class.getDeclaredConstructor(UUID.class, UUID.class, int.class, int.class);
        constructor.setAccessible(true);
        AudioEffect effect;
        try { effect = (AudioEffect) constructor.newInstance(TYPE, ANY, 0, session); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            // A newly advertised AudioTrack can precede its attachable private engine.
            // Use the same bounded readiness budget as the legacy Visualizer path.
            if (cause instanceof RuntimeException && cause.getMessage() != null
                    && cause.getMessage().matches(".*Error: -(?:3|5|7)$")) throw new MediaWaveCapture.NotReady(cause);
            throw error;
        }
        boolean configured = false, transferred = false;
        int restoreLatency = -1;
        try {
            if (!effect.hasControl()) throw new IllegalStateException("Private capture control unavailable");
            int[] value = new int[1];
            // Older HIDL implementations cannot read LATENCY. Leave them on the
            // established Visualizer API, without a firmware/version-name guess.
            if ((int) Reflect.call(get, effect, 2, value) != 1) return null;
            int latency = value[0];
            if (latency < 0 || latency > 3000) return null;
            restoreLatency = latency;
            require(effect.setEnabled(false)); configured = true;
            require((int) Reflect.call(set, effect, 0, MediaWaveSpectrum.SIZE));
            require((int) Reflect.call(set, effect, 1, 0)); // NORMALIZED, same as Visualizer.
            require(effect.setEnabled(true));
            var engine = new MediaWavePcmSource.Engine() {
                private final byte[] empty = new byte[0];
                public int samplingRateHz() { return rate; }
                public int latencyMs() throws MediaWaveCapture.NotReady {
                    if (!effect.hasControl() || !effect.getEnabled()) throw new MediaWaveCapture.NotReady("Capture stopped");
                    if ((int) Reflect.call(get, effect, 2, value) != 1) throw new MediaWaveCapture.NotReady("Capture latency unavailable");
                    return value[0];
                }
                public void latencyMs(int next) throws MediaWaveCapture.NotReady { require((int) Reflect.call(set, effect, 2, next)); }
                public void capture(byte[] pcm) throws MediaWaveCapture.NotReady {
                    if (!effect.hasControl() || !effect.getEnabled()
                            || (int) Reflect.call(command, effect, 0x10000, empty, pcm) != pcm.length)
                        throw new MediaWaveCapture.NotReady("Private PCM capture unavailable");
                }
                public void close() {
                    try { if (effect.hasControl()) effect.setEnabled(false); }
                    finally { effect.release(); }
                }
            };
            var result = new MediaWavePcmSource(engine, options, SystemClock::uptimeMillis, diagnostic,
                    () -> android.util.Log.isLoggable("COSMediaWave", android.util.Log.DEBUG));
            if (diagnostic != null) diagnostic.accept("Media wave capture: fresh PCM with visual-only delay=" + latency + " rate=" + rate);
            transferred = true;
            return result;
        } finally {
            // Ownership transfers only on a fully configured successful return.
            // A null capability result or any exception releases this single handle.
            if (!transferred) {
                try {
                    if (configured && effect.hasControl()) {
                        try { if (restoreLatency >= 0) Reflect.call(set, effect, 2, restoreLatency); }
                        finally { effect.setEnabled(false); }
                    }
                }
                finally { effect.release(); }
            }
        }
    }

    private static void require(int status) throws MediaWaveCapture.NotReady {
        if (status == AudioEffect.ERROR_NO_INIT || status == AudioEffect.ERROR_INVALID_OPERATION
                || status == AudioEffect.ERROR_DEAD_OBJECT) throw new MediaWaveCapture.NotReady("Private capture not ready");
        if (status != AudioEffect.SUCCESS) throw new IllegalStateException("Private capture command refused");
    }
}
