package dev.howard.colorosenhance.bridge;
import static org.junit.Assert.*;
import android.content.Intent;
import android.os.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import dev.howard.colorosenhance.*;
import dev.lsposed.fluidcloud.MediaWaveBands;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=EnhanceApplication.class,shadows=ParcelResultReceiverShadow.class)
public class SettingsBridgeServiceTest {
    SettingsBridgeService service; SettingsCoordinator coordinator; Store store;
    @Before public void setup() {
        ShadowProcess.setUid(10456); ShadowBinder.setCallingUid(10456);
        store = new Store();
        SettingsCoordinator.Source source = new SettingsCoordinator.Source() {
            public Object identity() { return this; }
            public PreferenceSession.Store open(String group) { return store; }
        };
        EnhanceApplication app = (EnhanceApplication) RuntimeEnvironment.getApplication();
        coordinator = new SettingsCoordinator(() -> source, () -> {});
        ReflectionHelpers.setField(app,"settings",coordinator);
        service = Robolectric.buildService(SettingsBridgeService.class).create().get();
    }
    @After public void teardown() { service.onDestroy(); }
    @Test public void transactionWritesOnlyValidatedSettingWithAcknowledgedRevision() throws Exception {
        Bundle request = command("put"); request.putString("group","battery"); request.putString("key","weight");
        request.putInt("value",650); request.putLong("revision",coordinator.read("battery").revision());
        Bundle result = send(request);
        assertEquals("ok",result.getString("status"));
        assertEquals(650,store.data.get("weight"));
        assertEquals(650,result.getBundle("values").getInt("weight"));
        assertEquals("conflict",send(request).getString("status"));
    }
    @Test public void selectedInstallerSurvivesBridgeRoundTripAndRejectsStalePicker() throws Exception {
        Bundle request=command("put");request.putString("group","installer");request.putString("key","target_package");
        request.putString("value","org.example.installer");request.putLong("revision",coordinator.read("installer").revision());
        Bundle result=send(request);assertEquals("ok",result.getString("status"));
        assertEquals("org.example.installer",result.getBundle("values").getString("target_package"));
        assertTrue(result.getString("target_label").contains("org.example.installer"));
        assertEquals("conflict",send(request).getString("status"));
    }
    @Test public void waveBandsRoundTripAndDedicatedResetReturnsTheUpdatedSnapshot() throws Exception {
        store.data.putAll(Map.of("enabled", true, "realtime_media_wave", true, "wave_interval_ms", 1000,
                "hide_capsule_arrows", true));
        Bundle edit = command("put");
        edit.putString("group", "cloud");
        edit.putString("key", MediaWaveBands.KEY);
        edit.putString("value", "40,180,315,800,2000,5000,16000");
        edit.putLong("revision", coordinator.read("cloud").revision());
        Bundle saved = send(edit);
        assertEquals("ok", saved.getString("status"));
        assertEquals(edit.getString("value"), saved.getBundle("values").getString(MediaWaveBands.KEY));

        Bundle reset = command("reset_wave");
        reset.putString("group", "battery");
        reset.putLong("revision", saved.getLong("revision"));
        assertEquals("invalid", send(reset).getString("status"));
        reset.putString("group", "cloud");
        Bundle cleared = send(reset);
        assertEquals("ok", cleared.getString("status"));
        assertFalse(cleared.getBundle("values").containsKey(MediaWaveBands.KEY));
        assertFalse(cleared.getBundle("values").containsKey("wave_interval_ms"));
        assertTrue(cleared.getBundle("values").getBoolean("realtime_media_wave"));
        assertTrue(cleared.getBundle("values").getBoolean("hide_capsule_arrows"));
        assertEquals(2, store.writes);
    }
    @Test public void unauthorizedCallerCannotReachConfigEvenWithCorrectProtocol() throws Exception {
        ShadowBinder.setCallingUid(10555);
        assertThrows(SecurityException.class, () -> send(command("snapshot")));
        assertEquals(0,store.writes);
    }
    @Test public void unknownOperationOrTypeCannotWriteAndSecretsAreNotReturned() throws Exception {
        for (String op : List.of("shell","memory_refresh_status","memory_refresh_start"))
            assertEquals("invalid",send(command(op)).getString("status"));
        Bundle removed = command("snapshot"); removed.putString("group","memory");
        assertEquals("invalid",send(removed).getString("status"));
        removed = command("put"); removed.putString("group","memory");
        removed.putString("key","enabled"); removed.putBoolean("value",true);
        assertEquals("invalid",send(removed).getString("status"));
        Bundle bad = command("put"); bad.putString("group","battery"); bad.putString("key","enabled");
        bad.putString("value","true");
        assertEquals("invalid",send(bad).getString("status"));
        store.data.put("future_private", "opaque");
        Bundle read = command("snapshot"); read.putString("group","battery");
        Bundle result = send(read);
        assertFalse(result.getBundle("values").containsKey("future_private"));
        assertEquals(0,store.writes);
    }
    @Test public void invalidSimInputsDoNotCreateControllerOrEchoPin() throws Exception {
        Bundle save = command("sim_save"); save.putInt("slot",8); save.putString("pin","4826"); save.putString("confirmation","4826");
        Bundle result = send(save);
        assertEquals("invalid",result.getString("status"));
        assertFalse(result.containsKey("pin")); assertFalse(result.containsKey("confirmation"));
    }
    @Test public void launcherVisibilityCanBeRestoredWithoutDisablingTheSettingsActivity() throws Exception {
        assertTrue(send(command("management_status")).getBoolean("launcher_visible"));
        Bundle request=command("launcher_visibility"); request.putBoolean("visible",false);
        Bundle hidden=send(request);
        assertEquals("ok",hidden.getString("status")); assertFalse(hidden.getBoolean("launcher_visible"));
        var pm=service.getPackageManager();
        assertNotNull("Hidden icon must retain a front door",pm.getLaunchIntentForPackage(dev.howard.colorosenhance.BuildConfig.APPLICATION_ID));
        var launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(dev.howard.colorosenhance.BuildConfig.APPLICATION_ID);
        assertTrue(pm.queryIntentActivities(launcher,0).isEmpty());
        request.putBoolean("visible",true);
        assertTrue(send(request).getBoolean("launcher_visible"));
        assertEquals(1,pm.queryIntentActivities(launcher,0).size());
        assertEquals(0,store.writes);
    }
    @Test public void managementRejectsMalformedWritesAndUnconfirmedRestart() throws Exception {
        Bundle bad=command("launcher_visibility"); bad.putString("visible","false");
        assertEquals("invalid",send(bad).getString("status"));
        assertEquals("invalid",send(command("restart_scopes")).getString("status"));
        ShadowBinder.setCallingUid(10555);
        assertThrows(SecurityException.class,()->send(command("management_status")));
    }
    @Test public void confirmedRestartIsCoalescedAndSnapshotReportsWorkerOutcome() throws Exception {
        EnhanceApplication app=(EnhanceApplication)service.getApplication(); ArrayDeque<Runnable> work=new ArrayDeque<>();
        var restart=new ScopeRestartController(work::add,Runnable::run,
                ()->new ScopeRestarter.Outcome(ScopeRestarter.Code.ROOT_DENIED,"all",List.of()),()->{});
        ReflectionHelpers.setField(app,"scopeRestart",restart);
        Bundle request=command("restart_scopes"); request.putBoolean("confirmed",true);
        assertEquals("accepted",send(request).getString("status"));
        assertEquals("busy",send(request).getString("status")); assertEquals(1,work.size());
        assertTrue(send(command("management_status")).getBoolean("restarting"));
        work.remove().run();
        Bundle done=send(command("management_status"));
        assertFalse(done.getBoolean("restarting")); assertTrue(done.getString("restart_message").contains("未获得 root"));
    }
    private Bundle send(Bundle request) throws Exception {
        List<Bundle> responses = new ArrayList<>();
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override protected void onReceiveResult(int code,Bundle data) { responses.add(data); }
        };
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BridgeContract.DESCRIPTOR); data.writeBundle(request); data.writeTypedObject(receiver,0);
            assertTrue("Bridge must dispatch a request",service.onBind(new Intent()).transact(BridgeContract.REQUEST,data,reply,0));
            reply.readException(); ShadowLooper.idleMainLooper();
            assertEquals(1,responses.size()); return responses.get(0);
        } finally { data.recycle(); reply.recycle(); }
    }
    private static Bundle command(String command) { Bundle b = new Bundle(); b.putString("op",command); return b; }
    private static class Store implements PreferenceSession.Store {
        Map<String,Object> data = new HashMap<>(); int writes;
        public Map<String,?> read() { return new HashMap<>(data); }
        public boolean replace(Map<String,Object> next) { writes++; data.clear(); data.putAll(next); return true; }
    }
}
