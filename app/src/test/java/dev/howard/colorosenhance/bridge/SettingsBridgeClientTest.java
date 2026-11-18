package dev.howard.colorosenhance.bridge;

import static org.junit.Assert.*;
import android.app.Application;
import android.content.*;
import android.os.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=36, application=Application.class, shadows=ParcelResultReceiverShadow.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SettingsBridgeClientTest {
    private Boundary context;
    private Endpoint endpoint;
    private SettingsBridgeClient client;
    private final List<Boolean> connections = new ArrayList<>();
    private final List<Bundle> replies = new ArrayList<>();
    private final List<Looper> callbackLoopers = new ArrayList<>();
    private int changes;

    @Before public void setup() {
        context = new Boundary(); endpoint = new Endpoint();
        Context host = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return context; }
            @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags) {
                throw new AssertionError("binding must use application context, never the Activity context");
            }
        };
        client = new SettingsBridgeClient(host, new SettingsBridgeClient.Listener() {
            public void onConnectionChanged(boolean connected) { connections.add(connected); callbackLoopers.add(Looper.myLooper()); }
            public void onChanged() { changes++; callbackLoopers.add(Looper.myLooper()); }
        });
    }
    @After public void tearDown() { if (endpoint.gate != null) endpoint.gate.countDown(); client.close(); }

    @Test public void explicitlyBindsOnceAndReceivesAcknowledgmentOnMain() throws Exception {
        connect(); client.connect();
        assertEquals(new ComponentName(dev.howard.colorosenhance.BuildConfig.APPLICATION_ID, "dev.howard.colorosenhance.bridge.SettingsBridgeService"), context.intent.getComponent());
        assertEquals(Context.BIND_AUTO_CREATE, context.flags);
        assertEquals(1, context.binds);
        assertTrue(client.isConnected());
        request("snapshot");
        Call call = endpoint.await("snapshot");
        assertNotSame(Looper.getMainLooper().getThread(), call.thread);
        Bundle response = status("conflict"); response.putLong("revision", 17);
        call.receiver.send(0, response);
        drainUntil(() -> replies.size() == 1);
        assertEquals("conflict", replies.get(0).getString("status"));
        assertEquals(17, replies.get(0).getLong("revision"));
        assertEquals(List.of(true), connections);
        assertTrue(callbackLoopers.stream().allMatch(looper -> looper == Looper.getMainLooper()));
        client.close(); assertEquals(1, context.unbinds);
    }

    @Test public void closeDropsDeferredOrdinaryAndWatchCallbacks() throws Exception {
        connect(); Call watch = endpoint.await("watch"); request("snapshot"); Call call = endpoint.await("snapshot");
        client.close();
        Call unwatch = endpoint.await("unwatch");
        assertSame(watch.request.getBinder("token"), unwatch.request.getBinder("token"));
        call.receiver.send(0,status("ok")); watch.receiver.send(0,status("changed"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertTrue(replies.isEmpty()); assertEquals(0,changes); assertFalse(client.isConnected());
        assertEquals(List.of(true),connections);
    }

    @Test public void disconnectCompletesUnavailableAndRejectsPriorGenerationResults() throws Exception {
        connect(); Call watch = endpoint.await("watch"); request("put"); Call first = endpoint.await("put");
        context.connection.onServiceDisconnected(context.intent.getComponent());
        drainUntil(() -> replies.size() == 1);
        assertEquals("unavailable",replies.get(0).getString("status"));
        assertFalse(client.isConnected());
        context.connection.onServiceConnected(context.intent.getComponent(),endpoint);
        endpoint.awaitCount("watch",2);
        acknowledgeLatestWatch();
        first.receiver.send(0,status("ok")); watch.receiver.send(0,status("changed"));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,replies.size()); assertEquals(0,changes);
        assertEquals(1,endpoint.count("put")); assertEquals(List.of(true,false,true),connections);
    }

    @Test public void timeoutIsDeliveredOnceAndWriteIsNeverReplayed() throws Exception {
        connect(); request("sim_save"); Call call = endpoint.await("sim_save");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4999));
        assertTrue(replies.isEmpty());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1,replies.size()); assertEquals("timeout",replies.get(0).getString("status"));
        call.receiver.send(0,status("accepted"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30));
        client.connect();
        assertEquals(1,replies.size()); assertEquals(1,endpoint.count("sim_save"));
    }

    @Test public void watchRemainsLiveAfterAcknowledgmentAndRepeatedEvents() throws Exception {
        connect(); Call watch = endpoint.await("watch");
        watch.receiver.send(0,status("ok")); watch.receiver.send(0,status("changed")); watch.receiver.send(0,status("changed"));
        drainUntil(() -> changes == 2);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        watch.receiver.send(0,status("changed")); drainUntil(() -> changes == 3);
        assertTrue(client.isConnected());
    }

    @Test public void failedBindIsUnavailableAndCloseDoesNotNotifyAgain() {
        context.accept = false; client.connect(); request("snapshot");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(client.isConnected()); assertEquals(List.of(false),connections);
        assertEquals("unavailable",replies.get(0).getString("status"));
        client.close(); client.connect(); assertEquals(1,context.binds);
    }

    @Test public void nullBindingAndBindingDeathReleaseBindingAndAllowExplicitRetry() {
        client.connect(); assertNotNull(context.connection); context.connection.onNullBinding(context.intent.getComponent());
        assertEquals(1,context.unbinds); assertFalse(client.isConnected());
        client.connect(); assertEquals(2,context.binds);
        context.connection.onBindingDied(context.intent.getComponent());
        assertEquals(2,context.unbinds); assertFalse(client.isConnected());
    }

    @Test public void limitsPendingRequestsWithoutSendingOverflow() throws Exception {
        connect();
        for (int i=0;i<17;i++) request("snapshot");
        drainUntil(() -> replies.size() == 1);
        assertEquals("unavailable",replies.get(0).getString("status"));
        endpoint.awaitCount("snapshot",16);
        assertEquals(16,endpoint.count("snapshot"));
    }

    @Test public void reconnectStormDropsStaleQueuedWatchesWithoutOverflowingWorker() throws Exception {
        endpoint.gate = new CountDownLatch(1);
        connect(); endpoint.await("watch");
        for (int i=0;i<30;i++) {
            context.connection.onServiceDisconnected(context.intent.getComponent());
            context.connection.onServiceConnected(context.intent.getComponent(),endpoint);
        }
        endpoint.gate.countDown();
        endpoint.awaitCount("watch",2); acknowledgeLatestWatch();
        request("snapshot"); Call call = endpoint.await("snapshot"); call.receiver.send(0,status("ok"));
        drainUntil(() -> replies.size() == 1);
        assertEquals("ok",replies.get(0).getString("status"));
        assertEquals(2,endpoint.count("watch"));
    }

    @Test public void connectionAndWritesWaitForWatchAcknowledgment() throws Exception {
        bind(); Call watch = endpoint.await("watch");
        assertFalse(client.isConnected()); assertTrue(connections.isEmpty());
        request("sim_save"); assertEquals("unavailable",replies.get(0).getString("status"));
        watch.receiver.send(0,status("changed")); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0,changes); assertFalse(client.isConnected());
        watch.receiver.send(0,status("ok")); drainUntil(client::isConnected);
        watch.receiver.send(0,status("ok")); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(true),connections); assertEquals(0,endpoint.count("sim_save"));
    }

    @Test public void rejectedWatchReleasesBindingAndNeverReportsConnected() throws Exception {
        for (String rejected : List.of("invalid","failed","unavailable")) {
            int count = context.binds+1;
            bind(); endpoint.awaitCount("watch",count);
            endpoint.latest("watch").receiver.send(0,status(rejected));
            drainUntil(() -> context.unbinds == count);
            assertFalse(client.isConnected()); assertFalse(connections.contains(true));
            request("put"); assertEquals("unavailable",replies.get(replies.size()-1).getString("status"));
        }
        assertEquals(0,endpoint.count("put"));
    }

    @Test public void missingWatchAcknowledgmentTimesOutAndLateAcknowledgmentCannotReconnect() throws Exception {
        bind(); Call watch=endpoint.await("watch");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4999));
        assertEquals(0,context.unbinds);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1,context.unbinds); assertFalse(client.isConnected());
        assertEquals(List.of(false),connections);
        watch.receiver.send(0,status("ok")); watch.receiver.send(0,status("changed"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(List.of(false),connections); assertEquals(0,changes); assertEquals(1,context.binds);
        Call unwatch=endpoint.await("unwatch");
        assertSame(watch.request.getBinder("token"),unwatch.request.getBinder("token"));
    }

    @Test public void closeDuringHandshakeCancelsDeadlineAndDropsLateAcknowledgment() throws Exception {
        bind(); Call watch=endpoint.await("watch"); client.close();
        watch.receiver.send(0,status("ok"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertTrue(connections.isEmpty()); assertFalse(client.isConnected()); assertEquals(1,context.unbinds);
    }

    private void bind() { client.connect(); assertNotNull("connect must bind the bridge",context.connection); context.connection.onServiceConnected(context.intent.getComponent(),endpoint); }
    private void connect() throws Exception { bind(); endpoint.await("watch"); acknowledgeLatestWatch(); }
    private void acknowledgeLatestWatch() throws Exception { endpoint.latest("watch").receiver.send(0,status("ok")); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private void request(String op) { client.request(command(op), result -> { replies.add(result); callbackLoopers.add(Looper.myLooper()); }); }
    private static Bundle command(String op) { Bundle result = new Bundle(); result.putString("op",op); return result; }
    private static Bundle status(String status) { Bundle result = new Bundle(); result.putString("status",status); return result; }
    private static void drainUntil(BooleanSupplier done) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while (!done.getAsBoolean() && System.nanoTime()<end) { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(2); }
        assertTrue("asynchronous operation must finish",done.getAsBoolean());
    }
    private static class Boundary extends ContextWrapper {
        Intent intent; ServiceConnection connection; int binds,unbinds,flags; boolean accept=true;
        Boundary() { super(RuntimeEnvironment.getApplication()); }
        @Override public Context getApplicationContext() { return this; }
        @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags) { this.intent=intent; this.connection=connection; this.flags=flags; binds++; return accept; }
        @Override public void unbindService(ServiceConnection connection) { assertSame(this.connection,connection); unbinds++; }
    }
    private record Call(Bundle request,ResultReceiver receiver,Thread thread) { }
    private static class Endpoint extends Binder {
        final List<Call> calls = new CopyOnWriteArrayList<>();
        volatile CountDownLatch gate;
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) {
            assertEquals(BridgeContract.REQUEST,code); data.enforceInterface(BridgeContract.DESCRIPTOR);
            Bundle request=data.readBundle(Bundle.class.getClassLoader());
            ResultReceiver receiver=data.readTypedObject(ResultReceiver.CREATOR); data.enforceNoDataAvail();
            calls.add(new Call(request,receiver,Thread.currentThread()));
            if (gate != null) {
                try { if (!gate.await(3,TimeUnit.SECONDS)) throw new AssertionError("test transaction was not released"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            }
            reply.writeNoException(); return true;
        }
        int count(String op) { return (int)calls.stream().filter(c -> op.equals(c.request.getString("op"))).count(); }
        void awaitCount(String op,int count) throws Exception { drainUntil(() -> count(op)>=count); }
        Call await(String op) throws Exception { awaitCount(op,1); return calls.stream().filter(c -> op.equals(c.request.getString("op"))).findFirst().orElseThrow(); }
        Call latest(String op) { return calls.stream().filter(c -> op.equals(c.request.getString("op"))).reduce((first,last) -> last).orElseThrow(); }
    }
}
