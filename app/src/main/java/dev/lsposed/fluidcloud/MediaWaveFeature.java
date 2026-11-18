package dev.lsposed.fluidcloud;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Single shared audio owner for OEM, shade and module-presented capsule views. */
final class MediaWaveFeature {
    private final HookEntry module;
    private final MediaWaveTrace trace;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Field contextField,mediaList,lastPlayer,devices;
    private final Method playerId,token,packageName,clientUid,sessionId,playerState,playerInterface;
    private final ArrayList<NativeMediaWave> plugins=new ArrayList<>();
    private final Set<Object> cards=Collections.newSetFromMap(new WeakHashMap<>());
    private final MediaWaveEnvelope envelope=new MediaWaveEnvelope();
    private MediaWaveOptions options=MediaWaveOptions.DEFAULTS;
    private final MediaWaveContinuity continuity=new MediaWaveContinuity();
    private final Runnable refresh=this::refresh;
    private final Runnable frame=this::frame;
    private final Runnable missingTrack=this::missingTrack;
    private WeakReference<Object> owner=new WeakReference<>(null);
    private Context context;
    private AudioManager audio;
    private MediaController media;
    private MediaSession.Token selected,destroyed;
    private List<AudioPlaybackConfiguration> playbacks=List.of();
    private boolean enabled,listening,screenReady,playing,resting,ended,queued,framing,rendering,blocked,waitingTrack,waitingVisibility;
    private int uid=-1,routeEpoch;
    private long lastFrame=-1,lastScreenCheck;
    private MediaWaveCapture.Request request;
    private Run run;
    private String diagnostic;
    private MediaController.Callback mediaCallback;
    private final AudioManager.AudioPlaybackCallback playbackCallback=new AudioManager.AudioPlaybackCallback(){
        @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations){playbacks=List.copyOf(configurations);changed();}
    };
    private final AudioDeviceCallback deviceCallback=new AudioDeviceCallback(){
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added){routeChanged();}
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed){routeChanged();}
    };
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            screenReady=Intent.ACTION_USER_PRESENT.equals(intent.getAction())
                    || !Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) && unlocked();
            refresh(); // release the effect promptly on screen-off
        }
    };
    MediaWaveFeature(HookEntry module,ClassLoader loader)throws ReflectiveOperationException{
        this.module=module;
        trace=new MediaWaveTrace(module::info);
        Class<?> controller=Reflect.type(loader,"com.oplus.systemui.media.seedling.livealert.MediaLiveAlertController");
        Class<?> data=Reflect.type(loader,"com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData");
        contextField=Reflect.field(controller,"sysContext",Context.class);
        mediaList=Reflect.field(controller,"mediaDataList",List.class);lastPlayer=Reflect.field(controller,"lastStatusBarPlayerId",String.class);
        playerId=Reflect.method(data,"getPlayerId",String.class);token=data.getMethod("getToken");packageName=Reflect.method(data,"getPackageName",String.class);
        clientUid=Reflect.method(AudioPlaybackConfiguration.class,"getClientUid",int.class);
        sessionId=Reflect.method(AudioPlaybackConfiguration.class,"getSessionId",int.class);
        playerState=Reflect.method(AudioPlaybackConfiguration.class,"getPlayerState",int.class);
        Method identity;
        try{identity=Reflect.method(AudioPlaybackConfiguration.class,"getPlayerInterfaceId",int.class);}
        catch(NoSuchMethodException older){identity=null;}
        playerInterface=identity;
        devices=Reflect.field(AudioPlaybackConfiguration.class,"mDeviceIds",int[].class);
    }
    void nativeMediaChanged(Object value){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->nativeMediaChanged(value));return;}
        owner=new WeakReference<>(value);
        if(context==null)context((Context)Reflect.get(contextField,value));
        if(enabled){selectMedia();changed();}
    }
    void context(Context value){
        if(context==null && value!=null){context=value;configurationChanged();}
    }
    NativeMediaWave addPlugin(ClassLoader loader,View root)throws ReflectiveOperationException{
        var plugin=new NativeMediaWave(module,root,loader,this);
        try{plugin.install();plugin.enabled(enabled);plugins.add(plugin);changed();return plugin;}
        catch(RuntimeException|LinkageError error){plugin.close();throw error;}
    }
    void removePlugin(NativeMediaWave plugin){if(plugin!=null){plugin.close();plugins.remove(plugin);changed();}}
    void card(Object controller,boolean visible){
        if(visible)cards.add(controller);else cards.remove(controller);
        if(enabled){checkScreen(true);refresh();}
    }
    void configurationChanged(){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::configurationChanged);return;}
        options=module.config.extras().waveOptions();envelope.configure(options);
        if(run!=null)run.loop.interval(options.intervalMs());
        boolean next=module.config.realtimeMediaWave();
        if(next!=enabled){
            enabled=next;blocked=false;
            if(!next){
                // Cancel audio before touching optional native UI/receiver contracts.
                Run previous=run;run=null;request=null;cancelTrackWait();
                if(previous!=null){previous.loop.request(null);previous.thread.quitSafely();}
                try{stopListening();}finally{stopOutput();}
            }
            for(var plugin:plugins)try{plugin.enabled(next);}
            catch(RuntimeException|LinkageError error){unavailable("Native waveform hook unavailable");}
        }
        if(enabled && !blocked && context!=null && !listening)startListening();
        if(enabled && !blocked){selectMedia();changed();}
    }
    private void startListening(){
        try{
            audio=context.getSystemService(AudioManager.class);if(audio==null)throw new IllegalStateException("No audio manager");
            screenReady=unlocked();listening=true;
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
            context.registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);
            audio.registerAudioPlaybackCallback(playbackCallback,main);audio.registerAudioDeviceCallback(deviceCallback,main);
            ensureRun();Run current=run;
            current.handler.post(()->{
                try{if(!enabled || run!=current)return;var initial=audio.getActivePlaybackConfigurations();main.post(()->{if(run==current && enabled){playbacks=List.copyOf(initial);changed();}});}
                catch(RuntimeException|LinkageError error){main.post(()->{if(run==current)unavailable("Playback configuration access unavailable");});}
            });
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){stopListening();unavailable("Media waveform capability unavailable");}
    }
    private boolean unlocked(){
        PowerManager power=context.getSystemService(PowerManager.class);KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
        return power!=null && keyguard!=null && power.isInteractive() && !keyguard.isKeyguardLocked();
    }
    private void checkScreen(boolean immediate){
        // A SystemUI restart can miss USER_PRESENT even though keyguard has gone away.
        // Reconcile on existing state/input events, never on waveform frames or a timer.
        long now=SystemClock.uptimeMillis();
        if(!screenReady && context!=null && (immediate || now-lastScreenCheck>=1000)){
            lastScreenCheck=now;screenReady=unlocked();
        }
    }
    private void stopListening(){
        boolean previous=listening;listening=false;
        if(previous){
            if(audio!=null){
                try{audio.unregisterAudioPlaybackCallback(playbackCallback);}catch(RuntimeException|LinkageError ignored){ }
                try{audio.unregisterAudioDeviceCallback(deviceCallback);}catch(RuntimeException|LinkageError ignored){ }
            }
            try{context.unregisterReceiver(screen);}catch(IllegalArgumentException ignored){ }
        }
        playbacks=List.of();
        if(media!=null)try{media.unregisterCallback(mediaCallback);}catch(RuntimeException|LinkageError ignored){ }
        media=null;selected=null;playing=false;resting=false;uid=-1;continuity.clear();
    }
    private void selectMedia(){
        if(!enabled || !listening)return;
        Object controller=owner.get();Object chosen=null;
        if(controller!=null){
            Object value=Reflect.get(mediaList,controller);String last=(String)Reflect.get(lastPlayer,controller);
            if(value instanceof List<?> list){
                for(Object item:list)if(item!=null && last!=null && last.equals(Reflect.call(playerId,item))){chosen=item;break;}
                if(chosen==null && list.size()==1)chosen=list.getFirst();
            }
        }
        MediaSession.Token next=chosen!=null && Reflect.call(token,chosen) instanceof MediaSession.Token t?t:null;
        if(next!=null && next.equals(destroyed))next=null;
        if(java.util.Objects.equals(next,selected))return;
        if(media!=null)media.unregisterCallback(mediaCallback);media=null;selected=next;playing=false;resting=false;uid=-1;
        retireOutput();continuity.source(next);
        if(next!=null)try{
            String name=(String)Reflect.call(packageName,chosen);
            uid=context.getPackageManager().getPackageUid(name,0);
            media=new MediaController(context,next);mediaCallback=callback(media,next);media.registerCallback(mediaCallback,main);
            updatePlayback(media.getPlaybackState());
        }catch(Exception|LinkageError error){selected=null;media=null;uid=-1;continuity.clear();}
    }
    private MediaController.Callback callback(MediaController controller,MediaSession.Token identity){
        return new MediaController.Callback(){
            private boolean current(){return media==controller && java.util.Objects.equals(selected,identity);}
            @Override public void onPlaybackStateChanged(PlaybackState state){
                if(!current())return;
                updatePlayback(state);
                changed();
            }
            @Override public void onSessionDestroyed(){
                if(!current())return;
                destroyed=identity;playing=false;resting=false;ended=true;retireOutput();continuity.clear();selectMedia();changed();
            }
        };
    }

    private void updatePlayback(PlaybackState state) {
        boolean wasPlaying = playing;
        boolean wasResting = resting;
        int status = state == null ? PlaybackState.STATE_NONE : state.getState();
        playing = status == PlaybackState.STATE_PLAYING;
        boolean terminal = MediaWaveTail.terminal(status);
        ended = continuity.playback(playing, terminal);
        resting = status == PlaybackState.STATE_PAUSED || (terminal && !ended);
        if (ended) {
            retireOutput();
        } else if (wasPlaying != playing || wasResting != resting) {
            cancelTrackWait();
            suspendMotion();
            if (!playing) cancelCapture();
        }
    }

    private void routeChanged(){routeEpoch++;changed();}
    void changed(){if(!enabled || blocked || queued)return;queued=true;main.post(refresh);}
    private void refresh(){
        queued=false;if(!enabled || blocked)return;
        try{
            checkScreen(false);
            MediaWaveCapture.Request next=null;boolean visible=false;waitingVisibility=false;
            boolean capsuleAvailable = screenReady && cards.isEmpty();
            if(listening && capsuleAvailable && playing && uid>=0){
                for(var plugin:plugins)if(!plugin.captureVisible().isEmpty()){visible=true;break;}
                if(visible)next=playback();
                else waitingVisibility=true;
            }
            if(android.util.Log.isLoggable("COSMediaWave",android.util.Log.DEBUG)){
                String state="Realtime media waveform readiness listening="+listening+" screen="+screenReady
                        +" playing="+playing+" source="+(uid>=0)+" visible="+visible+" route="+(next!=null)+" card="+!cards.isEmpty()+" custom="+continuity.custom()+" renderer="+rendering+" waitView="+waitingVisibility;
                if(!state.equals(diagnostic)){diagnostic=state;module.info(state);}
            }
            if(visible && next==null && continuity.custom()){
                if(!waitingTrack){waitingTrack=true;main.postDelayed(missingTrack,240);}
            }else cancelTrackWait();
            if (!java.util.Objects.equals(next, request)) {
                request=next;
                if (next != null) {
                    envelope.captureStarting();
                    stopFrames();
                    ensureRun();
                    run.loop.request(next);
                } else if (run != null) {
                    run.loop.request(null);
                }
            }
            syncVisibilityWatch();
            // Pausing or temporarily hiding the capsule releases audio, not its appearance.
            // Keep the six-bar renderer through native card animations and reacquisition.
            if(selected==null || uid<0 || ended){retireOutput();return;}
            if(continuity.custom())rendering=true;
            if (!capsuleAvailable || (!resting && next == null)) {
                suspendMotion();
            } else if (resting) {
                envelope.pause(options.holdPaused());
            }
            if(rendering){
                boolean presented=false;for(var plugin:plugins)presented|=plugin.present();
                if (!presented && request != null) {
                    cancelCapture();
                    suspendMotion();
                }
                if(presented)scheduleFrame();
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){unavailable("Media waveform state unavailable");}
    }
    private void cancelTrackWait(){waitingTrack=false;main.removeCallbacks(missingTrack);}
    private void missingTrack(){
        waitingTrack=false;
        if(!enabled || blocked || !screenReady || !playing || !cards.isEmpty() || request!=null)return;
        try{
            if(playback()!=null){changed();return;}
            // A short handoff may have no track. Persistent ambiguity/capture restrictions
            // must still fall back instead of leaving a frozen imitation of live audio.
            continuity.fallback();stopOutput();
        }catch(RuntimeException|LinkageError error){unavailable("Media waveform track unavailable");}
    }
    private MediaWaveCapture.Request playback(){
        AudioPlaybackConfiguration found=null;
        for(var config:playbacks){
            if((int)Reflect.call(clientUid,config)!=uid || (int)Reflect.call(playerState,config)!=2)continue;
            AudioAttributes attributes=config.getAudioAttributes();
            if(attributes.getUsage()!=AudioAttributes.USAGE_MEDIA || attributes.getAllowedCapturePolicy()==AudioAttributes.ALLOW_CAPTURE_BY_NONE)return null;
            if(found!=null)return null;found=config; // ambiguous/cross-faded streams retain OEM
        }
        if(found==null)return null;
        int id=(int)Reflect.call(sessionId,found);if(id<=0)return null;
        int route=31*(31*routeEpoch+continuity.epoch())+Arrays.hashCode((int[])Reflect.get(devices,found));
        int player=playerInterface==null?0:(int)Reflect.call(playerInterface,found);
        return new MediaWaveCapture.Request(uid,id,route,player);
    }
    private void ensureRun()throws ReflectiveOperationException{
        if(run==null)run=new Run(new AndroidMediaWaveBackend(()->module.config.extras().waveOptions(),message->{
            if(android.util.Log.isLoggable("COSMediaWave",android.util.Log.DEBUG))module.info(message);
        }));
    }
    private final class Run{
        final HandlerThread thread=new HandlerThread("ColorOS-MediaWave",Process.THREAD_PRIORITY_BACKGROUND);
        final Handler handler;
        final MediaWaveCapture loop;
        Run(MediaWaveCapture.Backend backend){
            thread.start();handler=new Handler(thread.getLooper());
            loop=new MediaWaveCapture(new MediaWaveCapture.Scheduler(){
                public void post(Runnable action,long delay){handler.postDelayed(action,delay);}
                public void remove(Runnable action){handler.removeCallbacks(action);}
            },backend,(ticket,levels)->main.post(()->accept(this,ticket,levels)),
                    (ticket,reason)->main.post(()->fallback(this,ticket,reason)));
            loop.interval(options.intervalMs());
        }
    }
    private void fallback(Run source,long ticket,String reason){
        if(run==source && ticket==source.loop.generation()){continuity.fallback();stopOutput();module.info("Realtime media waveform fallback: "+reason);}
    }
    private void accept(Run source,long ticket,MediaWaveLevels levels){
        if(run!=source || !enabled || !playing || !screenReady || !cards.isEmpty()
                || ticket!=source.loop.generation() || request==null)return;
        if(android.util.Log.isLoggable("COSMediaWave",android.util.Log.DEBUG))trace.sample(SystemClock.uptimeMillis(),levels);
        envelope.sample(levels);
        continuity.sampled();
        if(!rendering){
            rendering=true;
            boolean any=false;for(var plugin:plugins)any|=plugin.present();
            if(!any){rendering=false;changed();return;}
            module.info("Realtime media spectrum active: private PCM mixer, six frequency bands");
        }
        boolean visible=false;for(var plugin:plugins)visible|=plugin.activeVisible();
        if(!visible){changed();return;}
        // The same target still needs interpolation after a visibility interruption;
        // scheduleFrame itself excludes an already settled or hidden presentation.
        scheduleFrame();
    }
    private void scheduleFrame(){
        if(!rendering || framing || !screenReady || !cards.isEmpty() || !envelope.moving())return;
        boolean visible=false;for(var plugin:plugins)visible|=plugin.activeVisible();
        if(!visible)return;
        // No idle timer: a state transition can request one frame; only interpolation continues.
        long now = SystemClock.uptimeMillis();
        if (lastFrame < 0) lastFrame = now;
        framing = true;
        main.postDelayed(frame, Math.max(0, MediaWaveEnvelope.FRAME_MS - (now - lastFrame)));
    }
    private void frame(){
        framing=false;if(!rendering || !enabled)return;
        try{drawFrame();}
        catch(RuntimeException|LinkageError error){unavailable("Media waveform display unavailable");}
    }
    private void drawFrame(){
        // Recheck only local view/presentation facts, not Binder services, on animation frames.
        boolean any=false;for(var plugin:plugins)any|=plugin.activeVisible();
        if(!any){lastFrame=-1;changed();return;}
        long now=SystemClock.uptimeMillis();long elapsed=now-lastFrame;lastFrame=now;
        if(android.util.Log.isLoggable("COSMediaWave",android.util.Log.DEBUG))trace.frame(now);
        boolean moving=envelope.step(elapsed);
        for(var plugin:plugins)plugin.invalidate();
        if(moving){framing=true;main.postDelayed(frame,MediaWaveEnvelope.FRAME_MS);}
        else lastFrame=-1;
    }

    private void stopFrames() {
        main.removeCallbacks(frame);
        framing = false;
        lastFrame = -1;
    }

    private void suspendMotion() {
        envelope.suspend();
        stopFrames();
    }

    private void cancelCapture() {
        if (request == null) return;
        request = null;
        if (run != null) run.loop.request(null);
    }

    private void stopOutput(){
        waitingVisibility=false;syncVisibilityWatch();
        rendering=false;envelope.reset();stopFrames();
        for(var plugin:plugins)try{plugin.restore();}
        catch(RuntimeException|LinkageError error){module.error("Media waveform restore failed; native drawing retained",error);}
    }
    private void retireOutput(){
        // Audio/session ownership ends immediately. Already-bound waveforms alone keep
        // their last displayed pixels until the native outgoing capsule becomes hidden.
        cancelTrackWait();
        waitingVisibility=false;syncVisibilityWatch();
        cancelCapture();
        if(rendering){
            var levels=envelope.snapshot();
            for(var plugin:plugins)try{plugin.retire(levels);}
            catch(RuntimeException|LinkageError error){
                module.error("Media waveform exit retention unavailable",error);
                try{plugin.restore();}catch(RuntimeException|LinkageError restore){module.error("Media waveform exit restore failed",restore);}
            }
        }
        rendering=false;envelope.reset();stopFrames();
    }
    private void unavailable(String reason){
        if(run!=null)run.loop.request(null);request=null;blocked=true;cancelTrackWait();stopOutput();module.info(reason+"; OEM animation retained");
    }
    void renderFailure(){if(Looper.myLooper()==Looper.getMainLooper())unavailable("Media waveform rendering unavailable");else main.post(()->unavailable("Media waveform rendering unavailable"));}
    boolean rendering(){return rendering;}
    boolean awaitingVisibility(){return waitingVisibility && enabled && !blocked && listening && screenReady && playing && !ended && selected!=null && uid>=0 && cards.isEmpty() && request==null && !continuity.failed();}
    private void syncVisibilityWatch(){for(var plugin:plugins)plugin.awaitVisibility(awaitingVisibility());}
    boolean resumeOem(){return playing && screenReady;}
    boolean awaitingVisibleWave(){return enabled && !blocked && !continuity.failed() && listening && playing && screenReady && request==null && cards.isEmpty();}
    float level(int index){return envelope.at(index);}
    void waveformDrawn(boolean tail){if(android.util.Log.isLoggable("COSMediaWave",android.util.Log.DEBUG))trace.draw(SystemClock.uptimeMillis(),tail);}
    boolean visible(View view){return screenReady && module.mediaWaveVisible(view);}
    void pixels(View view){module.mediaWavePixels(view);}
}
