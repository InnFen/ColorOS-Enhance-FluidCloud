package dev.lsposed.fluidcloud;

import android.media.audiofx.Visualizer;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Runs only on the capture worker. Does not use shell/root, microphone or global session 0. */
final class AndroidMediaWaveBackend implements MediaWaveCapture.Backend {
    private final IBinder audio;
    private final java.util.function.Supplier<MediaWaveOptions> options;
    private final java.util.function.Consumer<String> diagnostic;
    private MediaWaveCapture.Request diagnosticRequest;
    private MediaWaveCapture.Request pcmRequest;
    private int pcmRate;
    private int requestNumber,checkNumber;
    AndroidMediaWaveBackend()throws ReflectiveOperationException{
        this(()->MediaWaveOptions.DEFAULTS);
    }
    AndroidMediaWaveBackend(java.util.function.Supplier<MediaWaveOptions> options)throws ReflectiveOperationException{
        this(options,message->{});
    }
    AndroidMediaWaveBackend(java.util.function.Supplier<MediaWaveOptions> options,java.util.function.Consumer<String> diagnostic)throws ReflectiveOperationException{
        this.options=options;
        this.diagnostic=diagnostic;
        Class<?> services=Class.forName("android.os.ServiceManager");
        audio=(IBinder)Reflect.call(Reflect.method(services,"getService",IBinder.class,String.class),null,"media.audio_flinger");
        if(audio==null)throw new IllegalStateException("Audio service unavailable");
    }
    @Override public boolean safe(MediaWaveCapture.Request request)throws Exception{
        return route(request)==MediaWaveCapture.Route.SAFE;
    }
    @Override public MediaWaveCapture.Route route(MediaWaveCapture.Request request)throws Exception{
        // A bounded read-only native snapshot once per acquisition, not a per-frame dumpsys.
        // No effect is created until the actual track and output thread have passed this check.
        if(!request.equals(diagnosticRequest)){diagnosticRequest=request;requestNumber++;checkNumber=0;}
        checkNumber++;
        pcmRequest=null;pcmRate=0;
        try{
            var assessment=AudioFlingerWaveRoute.assess(snapshot(),request.uid(),request.session());
            if(assessment.route()==MediaWaveCapture.Route.SAFE){pcmRequest=request;pcmRate=assessment.samplingRateHz();}
            diagnostic.accept("Media wave route check: request="+requestNumber+" check="+checkNumber+" "+assessment.route()+" "+assessment.reason());
            return assessment.route();
        }
        catch(Exception error){
            diagnostic.accept("Media wave route check: request="+requestNumber+" check="+checkNumber+" WAIT snapshot unavailable");
            throw new MediaWaveCapture.NotReady(error);
        }
    }
    private String snapshot()throws Exception{
        ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
        try(var read=pipe[0];var write=pipe[1]){
            audio.dumpAsync(write.getFileDescriptor(),new String[0]);write.close();
            var poll=new StructPollfd();poll.fd=read.getFileDescriptor();poll.events=(short)OsConstants.POLLIN;
            var descriptors=new StructPollfd[]{poll};var bytes=new ByteArrayOutputStream(32768);byte[] buffer=new byte[4096];
            long deadline=SystemClock.uptimeMillis()+250;
            while(bytes.size()<=262144){
                long remaining=deadline-SystemClock.uptimeMillis();
                if(remaining<=0 || Os.poll(descriptors,(int)remaining)==0)throw new IllegalStateException("Audio route check timed out");
                if((poll.revents&(OsConstants.POLLERR|OsConstants.POLLNVAL))!=0)throw new IllegalStateException("Audio route check failed");
                int size=Os.read(read.getFileDescriptor(),buffer,0,buffer.length);
                if(size==0)return bytes.toString(StandardCharsets.UTF_8);
                bytes.write(buffer,0,size);
            }
            throw new IllegalStateException("Audio route check too large");
        }
    }
    @Override public MediaWaveCapture.Source open(MediaWaveCapture.Request request)throws MediaWaveCapture.NotReady{
        // The allow-listed private PCM route has already been checked by the worker.
        // Newer AIDL engines expose their visual-only latency and can avoid their
        // broken wrapped capture window. Unsupported older engines keep the API path.
        try {
            MediaWaveCapture.Source fresh = request.equals(pcmRequest) && pcmRate > 0
                    ? AndroidMediaWavePcm.open(request.session(), pcmRate, options, diagnostic) : null;
            if (fresh != null) return fresh;
        } catch (MediaWaveCapture.NotReady waiting) {
            throw waiting;
        } catch (Exception | LinkageError unavailable) {
            diagnostic.accept("Media wave capture: platform Visualizer API (fresh PCM capability unavailable: "
                    + unavailable.getClass().getSimpleName() + ")");
        }
        Visualizer visualizer;
        try{visualizer=new Visualizer(request.session());} // never session 0
        catch(RuntimeException error){
            // Visualizer's Java wrapper exposes NO_INIT as a RuntimeException. A new
            // playback track can be advertised before its effect engine is attachable.
            if(error.getMessage()!=null && error.getMessage().endsWith("error: -3"))throw new MediaWaveCapture.NotReady(error);
            throw error;
        }
        return configure(visualizer, options, diagnostic);
    }
    static MediaWaveCapture.Source configure(Visualizer visualizer,java.util.function.Supplier<MediaWaveOptions> options)throws MediaWaveCapture.NotReady{
        return configure(visualizer, options, null);
    }
    private static MediaWaveCapture.Source configure(Visualizer visualizer,
            java.util.function.Supplier<MediaWaveOptions> options,
            java.util.function.Consumer<String> diagnostic) throws MediaWaveCapture.NotReady {
        try{
            int[] range=Visualizer.getCaptureSizeRange();
            int size=MediaWaveSpectrum.SIZE;
            if(range.length<2 || range[0]>size || range[1]<size)throw new IllegalStateException("FFT capture size unavailable");
            // Effect instances are keyed by session/type, and a freshly obtained handle
            // may inherit an enabled engine. Capture size can only change while disabled.
            require(visualizer.setEnabled(false),"disable");
            require(visualizer.setCaptureSize(size),"capture size");
            require(visualizer.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED),"scaling");
            require(visualizer.setEnabled(true),"activation");
            return new MediaWaveCapture.Source(){
                private final byte[] samples=new byte[size];
                private int rate;
                private MediaWaveBands bands;
                private MediaWaveSpectrum spectrum;
                private MediaWaveSpectrum previousSpectrum;
                private int traceReads;
                private MediaWaveInputTrace inputTrace;
                private byte[] traceWaveform;
                public MediaWaveLevels read()throws MediaWaveCapture.NotReady{
                    boolean tracing=diagnostic!=null && Log.isLoggable("COSMediaWave",Log.DEBUG);
                    long began=tracing?SystemClock.uptimeMillis():0;
                    // Native Visualizer can report SUCCESS with a zero buffer while
                    // disabled. That is not a measured silent frequency spectrum.
                    int actual;
                    try{
                        if(!visualizer.getEnabled())throw new MediaWaveCapture.NotReady("capture disabled");
                        actual=visualizer.getSamplingRate();
                    }catch(IllegalStateException state){throw new MediaWaveCapture.NotReady(state);}
                    if(actual<=0)throw new MediaWaveCapture.NotReady("sampling rate not ready");
                    MediaWaveOptions currentOptions = options.get();
                    MediaWaveBands selectedBands = currentOptions.bands();
                    if (spectrum == null || actual != rate || !selectedBands.equals(bands)) {
                        traceReads = diagnostic != null && Log.isLoggable("COSMediaWave", Log.DEBUG) ? 4 : 0;
                        previousSpectrum = traceReads > 0 && rate == actual ? spectrum : null;
                        spectrum = new MediaWaveSpectrum(actual, selectedBands);
                        rate = actual;
                        bands = selectedBands;
                    }
                    try{
                        if(visualizer.getFft(samples)!=Visualizer.SUCCESS || !visualizer.getEnabled())throw new MediaWaveCapture.NotReady("FFT not ready");
                    }catch(IllegalStateException state){throw new MediaWaveCapture.NotReady(state);}
                    int sensitivity = currentOptions.sensitivityPercent();
                    MediaWaveLevels levels = spectrum.read(samples, sensitivity);
                    if(tracing){
                        if(inputTrace==null)inputTrace=new MediaWaveInputTrace(diagnostic);
                        if(traceWaveform==null)traceWaveform=new byte[size];
                        byte[] observed=null;
                        try{if(visualizer.getWaveForm(traceWaveform)==Visualizer.SUCCESS)observed=traceWaveform;}
                        catch(RuntimeException ignored){/* Diagnostics cannot fail a valid FFT capture. */}
                        long now=SystemClock.uptimeMillis();inputTrace.read(now,now-began,samples,observed,levels,currentOptions);
                    }else{inputTrace=null;traceWaveform=null;}
                    if (traceReads > 0 && (--traceReads == 0 || !levels.equals(MediaWaveLevels.SILENT))) {
                        // Compare the same real FFT under both mappings. Only normalized bar
                        // heights are logged, once per mapping change when diagnostics are enabled.
                        String previous = previousSpectrum == null ? "" : " previous="
                                + percentLevels(previousSpectrum.read(samples, sensitivity));
                        diagnostic.accept("Media wave bands rateHz=" + actual / 1000 + " edges=" + selectedBands.encode()
                                + " bins=" + spectrum.binSummary() + " levels=" + percentLevels(levels) + previous);
                        previousSpectrum = null;
                        traceReads = 0;
                    }
                    return levels;
                }
                public void close(){try{visualizer.setEnabled(false);}finally{visualizer.release();}}
            };
        }catch(MediaWaveCapture.NotReady|RuntimeException|LinkageError error){visualizer.release();throw error;}
    }
    private static String percentLevels(MediaWaveLevels levels) {
        var values = new java.util.StringJoiner(",", "[", "]");
        for (int index = 0; index < MediaWaveLevels.COUNT; index++) {
            values.add(Integer.toString(Math.round(100 * levels.at(index))));
        }
        return values.toString();
    }
    private static void require(int status,String operation)throws MediaWaveCapture.NotReady{
        if(status==Visualizer.SUCCESS)return;
        if(status==Visualizer.ERROR_NO_INIT || status==Visualizer.ERROR_INVALID_OPERATION || status==Visualizer.ERROR_DEAD_OBJECT)
            throw new MediaWaveCapture.NotReady(operation+" not ready");
        throw new IllegalStateException("Visualizer refused "+operation);
    }
}
