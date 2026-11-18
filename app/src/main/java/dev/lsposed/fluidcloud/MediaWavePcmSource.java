package dev.lsposed.fluidcloud;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Avoids the AIDL Visualizer's latency-window wrap defect without altering audio output. */
final class MediaWavePcmSource implements MediaWaveCapture.Source {
    interface Engine extends AutoCloseable {
        int samplingRateHz() throws Exception;
        int latencyMs() throws Exception;
        void latencyMs(int value) throws Exception;
        void capture(byte[] pcm) throws Exception;
        @Override void close();
    }
    private record Frame(long due, MediaWaveLevels levels) { }
    private final Engine engine;
    private final Supplier<MediaWaveOptions> options;
    private final LongSupplier clock;
    private final MediaWavePcmFft transform = new MediaWavePcmFft();
    private final byte[] pcm = new byte[MediaWaveSpectrum.SIZE], fft = new byte[MediaWaveSpectrum.SIZE];
    private final ArrayDeque<Frame> pending = new ArrayDeque<>();
    private MediaWaveSpectrum spectrum;
    private MediaWaveBands bands;
    private MediaWaveLevels held = MediaWaveLevels.SILENT;
    private int rate, delayMs;
    private boolean primed;
    private MediaWaveInputTrace trace;
    private final Consumer<String> diagnostic;
    private final BooleanSupplier traceEnabled;

    MediaWavePcmSource(Engine engine, Supplier<MediaWaveOptions> options, LongSupplier clock) throws Exception {
        this(engine, options, clock, null, () -> false);
    }
    MediaWavePcmSource(Engine engine, Supplier<MediaWaveOptions> options, LongSupplier clock,
                       Consumer<String> diagnostic, BooleanSupplier traceEnabled) throws Exception {
        this.engine = engine; this.options = options; this.clock = clock;
        this.diagnostic = diagnostic; this.traceEnabled = traceEnabled;
        int latency = engine.latencyMs();
        if (latency < 0 || latency > 3000) throw new IllegalArgumentException("Unknown visualizer latency");
        delayMs = latency;
        // Read the currently aligned frame before switching only this capture window.
        engine.capture(pcm); held = levels(options.get()); primed = !held.equals(MediaWaveLevels.SILENT);
        engine.latencyMs(0);
        if (engine.latencyMs() != 0) throw new IllegalStateException("Capture latency did not settle");
    }

    @Override public MediaWaveLevels read() throws Exception {
        if (diagnostic != null && traceEnabled.getAsBoolean()) {
            if (trace == null) trace = new MediaWaveInputTrace(diagnostic);
        } else trace = null;
        long began = trace == null ? 0 : clock.getAsLong();
        // AudioFlinger may reconfigure this private effect when its mixer changes.
        int latency = engine.latencyMs();
        if (latency < 0 || latency > 3000) throw new IllegalStateException("Unknown visualizer latency");
        if (latency != 0) { delayMs = latency; engine.latencyMs(0); pending.clear(); primed=false; }
        engine.capture(pcm);
        MediaWaveOptions selected = options.get();
        MediaWaveLevels next = levels(selected);
        long now = clock.getAsLong();
        if (trace != null) trace.read(now, now - began, fft, pcm, next, selected);
        // Compensation is a bounded queue of six heights, never buffered audio, a new
        // timer, or synthetic motion. Bluetooth's audible latency remains accounted for.
        pending.addLast(new Frame(now + delayMs, next));
        while (!pending.isEmpty() && pending.getFirst().due() <= now) {
            held = pending.removeFirst().levels(); primed = true;
        }
        if (pending.size() > 152) throw new IllegalStateException("Capture timeline overflow");
        return held;
    }

    private MediaWaveLevels levels(MediaWaveOptions selected) throws Exception {
        int actual = engine.samplingRateHz();
        if (actual <= 0 || actual > 192000) throw new IllegalStateException("Unknown capture sampling rate");
        if (spectrum == null || rate != actual || !selected.bands().equals(bands)) {
            spectrum = new MediaWaveSpectrum(Math.multiplyExact(actual, 1000), selected.bands());
            rate = actual; bands = selected.bands(); pending.clear();
        }
        transform.transform(pcm, fft);
        return spectrum.read(fft, selected.sensitivityPercent());
    }

    @Override public void close() {
        pending.clear();
        // Engines may survive a client disconnect and be reused by the next capture.
        // Do not leave our zero capture-window hint behind for another client/resume.
        try { engine.latencyMs(delayMs); }
        catch (Exception | LinkageError ignored) { /* Lost control or already dead; never steal it back. */ }
        finally { engine.close(); }
    }
    @Override public boolean buffering() { return !primed; }
}
