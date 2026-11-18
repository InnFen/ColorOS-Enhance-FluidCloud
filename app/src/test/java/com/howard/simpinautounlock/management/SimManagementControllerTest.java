package com.howard.simpinautounlock.management;

import static org.junit.Assert.*;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import com.howard.simpinautounlock.ModuleApplication;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.ipc.ConfigContract;
import com.howard.simpinautounlock.ipc.PhoneBridgeClient;
import com.howard.simpinautounlock.pending.PendingPinRepository;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;
import com.howard.simpinautounlock.storage.AesGcmCipher;
import com.howard.simpinautounlock.storage.AndroidPreferenceStore;
import com.howard.simpinautounlock.storage.EncryptedPendingPinRepository;
import com.howard.simpinautounlock.storage.KeyProvider;
import com.howard.simpinautounlock.storage.PendingPinCodec;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = Application.class)
public class SimManagementControllerTest {
    private final MemoryPending repository = new MemoryPending();
    private final DeferredBridge bridge = new DeferredBridge();
    private SimManagementController controller;

    @Before public void createController() {
        controller = new SimManagementController(RuntimeEnvironment.getApplication(), repository, bridge);
    }

    @Test public void detachedFrontendDoesNotCancelSavePersistenceOrOtherObserver() {
        List<SimManagementController.Snapshot> first = new ArrayList<>();
        List<SimManagementController.Snapshot> second = new ArrayList<>();
        SimManagementController.Observer firstObserver = first::add;
        controller.addObserver(firstObserver);
        controller.addObserver(second::add);
        controller.save(0, "739251", "739251");
        assertNotNull("Save must start the phone request while availability is unknown", bridge.save);
        controller.removeObserver(firstObserver);
        int detachedCount = first.size();
        bridge.save.onResult(result(ConfigContract.RESULT_MODULE_UNAVAILABLE));
        assertEquals(PendingPinState.STAGED, repository.status(0).state());
        assertEquals(detachedCount, first.size());
        assertFalse(second.isEmpty());
        assertTrue(second.getLast().slots().getFirst().presentation().pendingWarningVisible());
        assertFalse(second.getLast().slots().getFirst().busy());
        assertFalse(second.toString().contains("739251"));
    }

    @Test public void invalidSlotOrPinDoesNotWriteOrSubmitAndReportsError() {
        controller.save(-1, "739251", "739251");
        controller.save(2, "739251", "739251");
        controller.clear(2);
        controller.setEnabled(-1, true);
        controller.save(0, null, null);
        controller.save(0, "123", "123");
        controller.save(1, "１２３４", "１２３４");
        controller.save(1, "739251", "different");
        assertEquals(0, repository.writes);
        assertEquals(0, bridge.actions);
        assertTrue(controller.snapshot().slots().get(0).error());
        assertTrue(controller.snapshot().slots().get(1).error());
        assertFalse(controller.snapshot().message().isEmpty());
    }

    @Test public void staleRefreshCannotReplaceCompletedSaveAndBusyRejectsDuplicateActions() {
        controller.refresh();
        assertEquals(2, bridge.queries.size());
        PhoneBridgeClient.Callback oldQuery = bridge.queries.getFirst();
        controller.save(0, "739251", "739251");
        assertTrue(controller.snapshot().slots().getFirst().busy());
        controller.save(0, "648203", "648203");
        controller.clear(0);
        controller.setEnabled(0, true);
        controller.clearAll();
        controller.refresh();
        assertEquals(1, bridge.actions);
        bridge.save.onResult(result(ConfigContract.RESULT_MODULE_UNAVAILABLE));
        SimManagementController.Slot completed = controller.snapshot().slots().getFirst();
        oldQuery.onResult(new PhoneBridgeClient.Result(ConfigContract.RESULT_OK, emptyStatus(0)));
        assertEquals(completed, controller.snapshot().slots().getFirst());
        assertEquals(1, repository.writes);
    }

    @Test public void initialSnapshotHasBothSlotsAndIsImmutable() {
        var snapshot = controller.snapshot();
        assertEquals(List.of(0, 1), snapshot.slots().stream().map(SimManagementController.Slot::slotId).toList());
        assertFalse(snapshot.slots().getFirst().presentation().toggleEnabled());
        assertFalse(snapshot.slots().getFirst().presentation().displayText().isBlank());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.slots().clear());
    }

    @Test public void detachedSavePersistsEncryptedBytesAndCanBeReadByFreshRepository() {
        Context context = RuntimeEnvironment.getApplication();
        var preferences = context.getSharedPreferences("synthetic-controller-test", Context.MODE_PRIVATE);
        KeyProvider keys = new KeyProvider() {
            final SecretKey key = new SecretKeySpec(new byte[32], "AES");
            @Override public SecretKey getOrCreate() { return key; }
            @Override public Optional<SecretKey> getIfPresent() { return Optional.of(key); }
            @Override public void delete() {}
        };
        PendingPinRepository encrypted = new EncryptedPendingPinRepository(
                new AndroidPreferenceStore(preferences), keys, new PendingPinCodec(), new AesGcmCipher(), () -> "synthetic-token");
        controller = new SimManagementController(context, encrypted, bridge);
        SimManagementController.Observer observer = snapshot -> {};
        controller.addObserver(observer);
        controller.save(1, "739251", "739251");
        controller.removeObserver(observer);
        bridge.save.onResult(result(ConfigContract.RESULT_MODULE_UNAVAILABLE));
        assertFalse(preferences.getAll().toString().contains("739251"));
        PendingPinRepository reopened = new EncryptedPendingPinRepository(
                new AndroidPreferenceStore(preferences), keys, new PendingPinCodec(), new AesGcmCipher(), () -> "synthetic-token");
        assertEquals("739251", reopened.peek(1).orElseThrow().pin());
        assertFalse(controller.snapshot().toString().contains("739251"));
    }

    @Test public void backgroundSubmissionAndCompletionOnlyNotifyAndPersistOnMain() throws Exception {
        List<Looper> deliveryThreads = new ArrayList<>();
        controller.addObserver(snapshot -> deliveryThreads.add(Looper.myLooper()));
        Thread submitter = new Thread(() -> controller.save(0, "739251", "739251"));
        submitter.start();
        submitter.join();
        assertNull(bridge.save);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(bridge.save);
        Thread completer = new Thread(() -> bridge.save.onResult(result(ConfigContract.RESULT_MODULE_UNAVAILABLE)));
        completer.start();
        completer.join();
        assertEquals(0, repository.writes);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(PendingPinState.STAGED, repository.status(0).state());
        assertTrue(deliveryThreads.stream().allMatch(looper -> looper == Looper.getMainLooper()));
    }

    @Test public void unavailableClearReportsLocalOnlyAndNeverClaimsPhoneWasCleared() {
        controller.refresh();
        bridge.queries.get(0).onResult(result(ConfigContract.RESULT_MODULE_UNAVAILABLE));
        controller.save(0, "739251", "739251");
        assertEquals(0, bridge.actions);
        controller.clear(0);
        assertEquals(PendingPinState.EMPTY, repository.status(0).state());
        assertTrue(controller.snapshot().slots().getFirst().message().contains("未确认电话端记录已清除"));
        assertFalse(controller.snapshot().slots().getFirst().error());
        controller.clearAll();
        assertEquals(0, bridge.actions);
        assertFalse(controller.snapshot().message().isEmpty());
    }

    @Test public void rejectedPhoneSaveKeepsExistingStagingAndShowsError() {
        repository.stage(0, "648203");
        controller.save(0, "739251", "739251");
        bridge.save.onResult(result(ConfigContract.RESULT_SIM_UNAVAILABLE));
        assertEquals("648203", repository.claim(0).orElseThrow().pin());
        assertEquals(1, repository.writes);
        assertTrue(controller.snapshot().slots().getFirst().error());
        assertFalse(controller.snapshot().slots().getFirst().busy());
    }

    @Test public void successfulPhoneSaveClearsStagingAndQueriesAuthoritativeStatus() {
        repository.stage(0, "648203");
        controller.save(0, "739251", "739251");
        bridge.save.onResult(result(ConfigContract.RESULT_OK));
        assertEquals(PendingPinState.EMPTY, repository.status(0).state());
        assertEquals(1, bridge.queries.size());
        bridge.queries.getFirst().onResult(new PhoneBridgeClient.Result(ConfigContract.RESULT_OK,
                new SlotStatus(0, SlotState.ARMED, "3456", "3456", true, -1, -1, -1)));
        assertTrue(controller.snapshot().slots().getFirst().presentation().toggleState());
        assertFalse(controller.snapshot().slots().getFirst().busy());
    }

    @Test @Config(application = LifecycleApplication.class)
    public void singletonOwnsOneRepositoryRegistrationAcrossFrontendLifecycles() {
        LifecycleApplication application = (LifecycleApplication) RuntimeEnvironment.getApplication();
        assertEquals(0, application.registrations);
        SimManagementController first = application.simManagement();
        SimManagementController.Observer observer = snapshot -> {};
        first.addObserver(observer);
        first.removeObserver(observer);
        assertSame(first, application.simManagement());
        assertEquals(1, application.registrations);
    }

    public static class LifecycleApplication extends ModuleApplication {
        int registrations;
        @Override public void onCreate() {}
        @Override public Registration observeRemoteRepository(RemoteRepositoryListener listener) {
            registrations++;
            return () -> {};
        }
    }

    static PhoneBridgeClient.Result result(int code) { return new PhoneBridgeClient.Result(code, null); }
    static SlotStatus emptyStatus(int slot) {
        return new SlotStatus(slot, SlotState.EMPTY, "", "", false, -1, -1, -1);
    }
    static final class DeferredBridge implements SimManagementController.Bridge {
        final List<PhoneBridgeClient.Callback> queries = new ArrayList<>();
        PhoneBridgeClient.Callback save;
        int actions;
        @Override public void query(int slotId, PhoneBridgeClient.Callback callback) { queries.add(callback); }
        @Override public void configure(int slotId, String pin, PhoneBridgeClient.Callback callback) { actions++; save = callback; }
        @Override public void setEnabled(int slotId, boolean enabled, PhoneBridgeClient.Callback callback) { actions++; }
        @Override public void clearSlot(int slotId, PhoneBridgeClient.Callback callback) { actions++; }
        @Override public void clearAll(PhoneBridgeClient.Callback callback) { actions++; }
    }
    static final class MemoryPending implements PendingPinRepository {
        final String[] pins = new String[2];
        int writes;
        @Override public void stage(int slotId, String pin) { writes++; pins[slotId] = pin; }
        @Override public PendingPinStatus status(int slotId) {
            return new PendingPinStatus(slotId, pins[slotId] == null ? PendingPinState.EMPTY : PendingPinState.STAGED);
        }
        @Override public Optional<Claim> claim(int slotId) {
            return pins[slotId] == null ? Optional.empty() : Optional.of(new Claim(pins[slotId], "synthetic-token"));
        }
        @Override public boolean acknowledge(int slotId, String token) { return false; }
        @Override public void clear(int slotId) { writes++; pins[slotId] = null; }
        @Override public void clearAll() { writes++; pins[0] = null; pins[1] = null; }
    }
}
