package dev.lsposed.fluidcloud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, manifest = Config.NONE)
public class MediaPauseRetentionTest {
    private MediaPauseRetention retention;
    private FakeController controller;
    private boolean sessionAlive;
    private int liveState;

    @Before public void setUp() throws Exception {
        sessionAlive = true;
        liveState = 2;
        retention = new MediaPauseRetention(FakeController.class, FakeMedia.class,
                (context, token, packageName) -> sessionAlive && (liveState==0 || MediaPauseRetention.continuingState(liveState)));
        controller = new FakeController(RuntimeEnvironment.getApplication());
        controller.mediaDataList.add(new FakeMedia("player-1", "music.app", true, 2, new Object()));
    }

    private void send(boolean enabled) {
        retention.configurationChanged(enabled);
        MediaPauseRetention.SendState retained = retention.beforeSend(controller, "sendLiveAlert", enabled);
        controller.sendLiveAlertData("sendLiveAlert");
        retention.afterSend(controller, retained);
    }

    @Test public void pausedActiveSessionStaysInNativeStatusBarBeyondOemTimeout() {
        send(true);
        assertTrue(controller.sentHostMap);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertTrue(controller.sentHostMap);
        assertTrue(controller.showOnStatusBar);
    }

    @Test public void disabledSwitchKeepsTheOriginalFiveSecondHide() {
        send(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertFalse(controller.sentHostMap);
    }

    @Test public void endedSessionOrInactiveDataCannotBeRetained() {
        sessionAlive = false;
        send(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertFalse(controller.sentHostMap);
        controller.showOnStatusBar = true;
        controller.mediaDataList.set(0, new FakeMedia("player-1", "music.app", false, 2, new Object()));
        send(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertFalse(controller.sentHostMap);
    }

    @Test public void newLiveUnplayedPlayerCanShowWithoutInventingPlaybackHistory() {
        controller.mediaDataList.set(0, new FakeMedia("player-2", "music.app", true, 2, new Object()));
        send(true);
        assertTrue(controller.sentHostMap);
        assertEquals("player-1",controller.lastStatusBarPlayerId);
        assertEquals(2,controller.mediaDataList.getFirst().getState());
    }

    @Test public void neverPlayedLiveCardCanShowAndStoppedCardCannot() {
        controller.lastStatusBarPlayerId="";
        controller.showOnStatusBar=false;
        controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,0,new Object()));
        send(true);
        assertTrue(controller.sentHostMap);
        assertEquals("",controller.lastStatusBarPlayerId);
        controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,1,new Object()));
        send(true);
        assertFalse(controller.sentHostMap);
    }

    @Test public void firstPlayKeepsVisibleWhileSessionAndOemCardUpdatesArriveSeparately(){
        controller.lastStatusBarPlayerId="";controller.showOnStatusBar=false;liveState=0;
        controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,0,new Object()));
        send(true);assertTrue(controller.sentHostMap);assertEquals("",controller.lastStatusBarPlayerId);
        liveState=6; // Live session is buffering while the OEM card still says never played.
        send(true);assertTrue(controller.sentHostMap);
        controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,6,new Object()));
        send(true);assertTrue(controller.sentHostMap);assertEquals("",controller.lastStatusBarPlayerId);
        liveState=3; // Playback starts before the OEM card's matching playing update.
        send(true);assertTrue(controller.sentHostMap);
        controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,3,new Object()));
        send(true);assertTrue(controller.sentHostMap);assertEquals("new-media",controller.lastStatusBarPlayerId);
        liveState=2;controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,2,new Object()));
        send(true);assertTrue(controller.sentHostMap);assertEquals("new-media",controller.lastStatusBarPlayerId);
        liveState=1;controller.mediaDataList.set(0,new FakeMedia("new-media","media.app",true,1,new Object()));
        send(true);assertFalse(controller.sentHostMap);
    }

    @Test public void turningOffAnExistingHoldRestoresNativeHiddenResult() {
        send(true);
        retention.configurationChanged(false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(controller.sentHostMap);
    }

    @Test public void nativeFirstPlayIdentityMustSurviveAnUpdateBetweenHookAndNativeSend() {
        controller.lastStatusBarPlayerId="";controller.showOnStatusBar=false;
        controller.mediaDataList.set(0,new FakeMedia("first-player","music.app",true,2,new Object()));
        var retained=retention.beforeSend(controller,"sendLiveAlert",true);
        // The OEM data is mutable and is populated on a different handler. A
        // first playing update can arrive after the hook reads a paused card.
        controller.mediaDataList.set(0,new FakeMedia("first-player","music.app",true,3,new Object()));
        controller.sendLiveAlertData("sendLiveAlert");retention.afterSend(controller,retained);
        assertTrue(controller.sentHostMap);
        assertEquals("first-player",controller.lastStatusBarPlayerId);
        sessionAlive=false;
        controller.mediaDataList.set(0,new FakeMedia("first-player","music.app",true,2,new Object()));
        send(true);
        assertTrue(controller.sentHostMap); // Native playing history still owns the first pause.
    }

    @Test public void turningOnWhileStillPausedRestoresCapsuleWithoutChangingPlayback() {
        send(false);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertFalse(controller.sentHostMap);
        retention.configurationChanged(true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(controller.sentHostMap);
        assertEquals(2, controller.mediaDataList.get(0).getState());
    }

    public static final class FakeMedia {
        private final String playerId, packageName;
        private final boolean active;
        private final int state;
        private final Object token;
        FakeMedia(String playerId, String packageName, boolean active, int state, Object token) {
            this.playerId=playerId; this.packageName=packageName; this.active=active;
            this.state=state; this.token=token;
        }
        public String getPlayerId() { return playerId; }
        public String getPackageName() { return packageName; }
        public boolean getActive() { return active; }
        public int getState() { return state; }
        public Object getToken() { return token; }
    }

    public static final class FakeController {
        private final Context sysContext;
        private final Handler myHandler = new Handler(Looper.getMainLooper());
        private final Runnable statusBarTimeoutRunnable = () -> {
            showOnStatusBar = false;
            sendLiveAlertData("sendLiveAlert");
        };
        private final List<FakeMedia> mediaDataList = new ArrayList<>();
        private String lastStatusBarPlayerId = "player-1";
        private boolean isMediaCardDestroy;
        private boolean showOnStatusBar = true;
        boolean sentHostMap;
        FakeController(Context context) { sysContext = context; }
        public boolean sendLiveAlertData(String method) {
            if (mediaDataList.isEmpty()) { sentHostMap = false; return false; }
            FakeMedia media = mediaDataList.get(0);
            if(media.getState()==3){
                lastStatusBarPlayerId=media.getPlayerId();showOnStatusBar=true;myHandler.removeCallbacks(statusBarTimeoutRunnable);
            }
            if (media.getState() == 2 && showOnStatusBar
                    && !myHandler.hasCallbacks(statusBarTimeoutRunnable)) {
                myHandler.postDelayed(statusBarTimeoutRunnable, 5000);
            }
            if (!lastStatusBarPlayerId.equals(media.getPlayerId())) showOnStatusBar = false;
            if(media.getState()==1)showOnStatusBar=false;
            sentHostMap = showOnStatusBar;
            return true;
        }
    }
}
