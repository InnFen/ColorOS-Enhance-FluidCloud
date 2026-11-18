package dev.lsposed.fluidcloud;

import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/** Retains a live, nonplaying OEM media card without inventing a playback history. */
final class MediaPauseRetention {
    record SendState(String originalPlayer, String currentPlayer) { }
    interface PlaybackSessionCheck {
        boolean retainable(Context context, Object token, String packageName);
    }

    private final Field contextField, handlerField, timeoutField, dataField;
    private final Field visibleField, destroyedField, lastPlayerField;
    private final Method send, playerId, packageName, active, state, token;
    private final PlaybackSessionCheck sessionCheck;
    private final Consumer<Throwable> failure;
    private volatile boolean enabled;
    private volatile WeakReference<Object> controller = new WeakReference<>(null);
    private boolean holding;

    MediaPauseRetention(Class<?> controllerType, Class<?> mediaType,
                        PlaybackSessionCheck sessionCheck) throws ReflectiveOperationException {
        this(controllerType, mediaType, sessionCheck, error -> { });
    }

    MediaPauseRetention(Class<?> controllerType, Class<?> mediaType,
                        PlaybackSessionCheck sessionCheck,
                        Consumer<Throwable> failure) throws ReflectiveOperationException {
        contextField = Reflect.field(controllerType, "sysContext", Context.class);
        handlerField = Reflect.field(controllerType, "myHandler", Handler.class);
        timeoutField = Reflect.field(controllerType, "statusBarTimeoutRunnable", Runnable.class);
        dataField = Reflect.field(controllerType, "mediaDataList", List.class);
        visibleField = Reflect.field(controllerType, "showOnStatusBar", boolean.class);
        destroyedField = Reflect.field(controllerType, "isMediaCardDestroy", boolean.class);
        lastPlayerField = Reflect.field(controllerType, "lastStatusBarPlayerId", String.class);
        send = Reflect.method(controllerType, "sendLiveAlertData", boolean.class, String.class);
        playerId = Reflect.method(mediaType, "getPlayerId", String.class);
        packageName = Reflect.method(mediaType, "getPackageName", String.class);
        active = Reflect.method(mediaType, "getActive", boolean.class);
        state = Reflect.method(mediaType, "getState", int.class);
        token = mediaType.getMethod("getToken");
        if (token.getParameterCount() != 0) throw new NoSuchMethodException("getToken arguments");
        this.sessionCheck = sessionCheck;
        this.failure = failure;
    }

    static boolean liveRetainableSession(Context context, Object token, String packageName) {
        if (!(token instanceof MediaSession.Token session) || packageName == null) return false;
        try {
            MediaController controller = new MediaController(context, session);
            PlaybackState playback = controller.getPlaybackState();
            if (!packageName.equals(controller.getPackageName())) return false;
            // Session state can lead the OEM card during the first play request. Keep the same
            // still-paused card visible until the native playing update establishes its history.
            if (playback != null && continuingState(playback.getState())) return true;
            if (playback != null && playback.getState() != PlaybackState.STATE_NONE) return false;
            MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
            return manager != null && manager.getActiveSessions(null).stream()
                    .anyMatch(item -> session.equals(item.getSessionToken())
                            && packageName.equals(item.getPackageName()));
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static boolean continuingState(int state) {
        return state==PlaybackState.STATE_PAUSED || state==PlaybackState.STATE_PLAYING
                || state==PlaybackState.STATE_BUFFERING || state==PlaybackState.STATE_CONNECTING
                || state==PlaybackState.STATE_FAST_FORWARDING || state==PlaybackState.STATE_REWINDING
                || state==PlaybackState.STATE_SKIPPING_TO_PREVIOUS || state==PlaybackState.STATE_SKIPPING_TO_NEXT
                || state==PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
    }

    SendState beforeSend(Object target, String method, boolean enabledNow) {
        controller = new WeakReference<>(target);
        enabled = enabledNow;
        if (!"sendLiveAlert".equals(method) || !enabledNow || !eligible(target)) {
            holding = false;
            return null;
        }
        String current = currentPlayer(target);
        SendState result = new SendState((String) Reflect.get(lastPlayerField,target),current);
        setPlayer(target,current);
        setVisible(target, true);
        cancelTimeout(target);
        holding = true;
        return result;
    }

    void afterSend(Object target, SendState retained) {
        if (retained == null) return;
        try { if (enabled) cancelTimeout(target); }
        finally {
            if (retained.currentPlayer().equals(Reflect.get(lastPlayerField,target))) {
                // A live first-play update can land while the native sender is
                // building the notification. Do not roll back the playing
                // identity that it just established to the pre-play empty ID.
                // This is native playback history, not our temporary paused ID.
                if (nativePlayingIdentity(target, retained.currentPlayer())) holding = false;
                else setPlayer(target,retained.originalPlayer());
            }
        }
    }

    private boolean nativePlayingIdentity(Object target, String current) {
        List<?> items = (List<?>) Reflect.get(dataField, target);
        if (items == null || items.isEmpty()) return false;
        Object item = items.getFirst();
        if (item == null || !current.equals(Reflect.call(playerId, item))) return false;
        int playback = (int) Reflect.call(state, item);
        // Keep genuine playing/transport states, never a retained paused,
        // buffering or connecting identity.
        return continuingState(playback) && playback != PlaybackState.STATE_PAUSED
                && playback != PlaybackState.STATE_BUFFERING && playback != PlaybackState.STATE_CONNECTING;
    }

    void trace(Object target, String phase, String method) {
        if (!android.util.Log.isLoggable("COSMediaHold", android.util.Log.DEBUG)) return;
        List<?> items = (List<?>) Reflect.get(dataField, target);
        Object item = items == null || items.isEmpty() ? null : items.getFirst();
        android.util.Log.i("COSMediaHold", phase + " " + method + " count=" + (items == null ? 0 : items.size())
                + " destroyed=" + Reflect.get(destroyedField, target) + " visible=" + Reflect.get(visibleField, target)
                + " enabled=" + enabled + " holding=" + holding
                + (item == null ? "" : " state=" + Reflect.call(state, item) + " active=" + Reflect.call(active, item)
                + " nativeIdentity=" + java.util.Objects.equals(Reflect.get(lastPlayerField, target), Reflect.call(playerId, item))));
    }

    void configurationChanged(boolean value) {
        if (enabled == value) return;
        enabled = value;
        Object target = controller.get();
        if (target == null) return;
        handler(target).post(() -> {
            try {
                if (enabled) {
                    if (!eligible(target)) return;
                    SendState attempt=beforeSend(target,"sendLiveAlert",true);
                    try { Reflect.call(send,target,"sendLiveAlert"); }
                    finally { afterSend(target,attempt); }
                } else if (holding) {
                    holding = false;
                    if (eligible(target)) {
                        setVisible(target, false);
                        cancelTimeout(target);
                        Reflect.call(send, target, "sendLiveAlert");
                    }
                }
            } catch (RuntimeException | LinkageError error) {
                enabled = false;
                holding = false;
                failure.accept(error);
            }
        });
    }

    private boolean eligible(Object target) {
        if (Boolean.TRUE.equals(Reflect.get(destroyedField, target))) return false;
        List<?> items = (List<?>) Reflect.get(dataField, target);
        if (items == null || items.isEmpty()) return false;
        Object item = items.get(0);
        if (item == null || !(boolean) Reflect.call(active, item)) return false;
        int playback = (int) Reflect.call(state,item);
        if (playback != PlaybackState.STATE_PAUSED && playback != PlaybackState.STATE_NONE
                && playback != PlaybackState.STATE_BUFFERING && playback != PlaybackState.STATE_CONNECTING) return false;
        String id = (String) Reflect.call(playerId, item);
        if (id == null || id.isBlank()) return false;
        Object session = Reflect.call(token, item);
        return session != null && sessionCheck.retainable((Context) Reflect.get(contextField, target),
                session, (String) Reflect.call(packageName, item));
    }
    private String currentPlayer(Object target) {
        return (String) Reflect.call(playerId,((List<?>)Reflect.get(dataField,target)).getFirst());
    }
    private void setPlayer(Object target,String value) {
        try { lastPlayerField.set(target,value); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Media player identity",error); }
    }

    private Handler handler(Object target) { return (Handler) Reflect.get(handlerField, target); }
    private void setVisible(Object target, boolean value) {
        try { visibleField.setBoolean(target, value); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Media status-bar visibility", error); }
    }
    private void cancelTimeout(Object target) {
        handler(target).removeCallbacks((Runnable) Reflect.get(timeoutField, target));
    }
}
