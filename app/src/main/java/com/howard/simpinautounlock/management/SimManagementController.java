package com.howard.simpinautounlock.management;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.howard.simpinautounlock.ModuleApplication;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.ipc.ConfigContract;
import com.howard.simpinautounlock.ipc.PhoneBridgeClient;
import com.howard.simpinautounlock.pending.MigratingPendingPinRepository;
import com.howard.simpinautounlock.pending.PendingPinRepository;
import com.howard.simpinautounlock.pending.PendingPinRepositoryFactory;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;
import com.howard.simpinautounlock.ui.SaveRoutingPolicy;
import com.howard.simpinautounlock.ui.SlotCallbackGate;
import com.howard.simpinautounlock.ui.SlotPresentation;
import dev.howard.colorosenhance.R;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Application-owned SIM operations. Only immutable, credential-free state leaves this class. */
public final class SimManagementController {
    public record Slot(int slotId, SlotPresentation presentation,
                       String message, boolean error, boolean busy) {}
    public record Snapshot(List<Slot> slots, String message) {
        public Snapshot { slots = List.copyOf(slots); }
    }
    public interface Observer { void onChanged(Snapshot snapshot); }

    interface Bridge {
        void query(int slotId, PhoneBridgeClient.Callback callback);
        void configure(int slotId, String pin, PhoneBridgeClient.Callback callback);
        void setEnabled(int slotId, boolean enabled, PhoneBridgeClient.Callback callback);
        void clearSlot(int slotId, PhoneBridgeClient.Callback callback);
        void clearAll(PhoneBridgeClient.Callback callback);
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PendingPinRepository pendingRepository;
    private final Bridge bridge;
    private final List<Observer> observers = new ArrayList<>();
    private final SlotCallbackGate.Owner callbacks = new SlotCallbackGate().replaceOwner();
    private final SaveRoutingPolicy.Availability[] availability = {
            SaveRoutingPolicy.Availability.UNKNOWN, SaveRoutingPolicy.Availability.UNKNOWN
    };
    private final Slot[] slots = new Slot[2];
    private volatile Snapshot current;
    private String pageMessage = "";
    // The registration deliberately lives with the application, independent of all frontends.
    private ModuleApplication.Registration repositoryRegistration;

    public SimManagementController(ModuleApplication application) {
        this(application, new MigratingPendingPinRepository(
                PendingPinRepositoryFactory.create(application)), adapt(new PhoneBridgeClient(application)));
        onMain(() -> repositoryRegistration = application.observeRemoteRepository(
                new ModuleApplication.RemoteRepositoryListener() {
                    @Override public void onAvailable(PendingPinRepository repository) {
                        onMain(() -> {
                            try {
                                ((MigratingPendingPinRepository) pendingRepository).attachRemote(repository);
                                refreshOnMain();
                            } catch (RuntimeException failure) {
                                showBothErrors(text(R.string.sim_error_local_storage));
                                publish();
                            }
                        });
                    }
                    @Override public void onUnavailable() {
                        onMain(() -> ((MigratingPendingPinRepository) pendingRepository).detachRemote());
                    }
                }));
    }

    SimManagementController(Context context, PendingPinRepository repository, Bridge bridge) {
        this.context = context.getApplicationContext();
        this.pendingRepository = Objects.requireNonNull(repository);
        this.bridge = Objects.requireNonNull(bridge);
        for (int slotId = 0; slotId < 2; slotId++) {
            slots[slotId] = new Slot(slotId, loading(), "", false, false);
        }
        current = new Snapshot(List.of(slots[0], slots[1]), "");
    }

    public Snapshot snapshot() { return current; }
    public void addObserver(Observer observer) {
        Objects.requireNonNull(observer);
        onMain(() -> {
            if (!observers.contains(observer)) observers.add(observer);
            notifyObserver(observer, current);
        });
    }
    public void removeObserver(Observer observer) { onMain(() -> observers.remove(observer)); }
    public void refresh() { onMain(this::refreshOnMain); }
    public void save(int slotId, String pin, String confirmation) {
        onMain(() -> saveOnMain(slotId, pin, confirmation));
    }
    public void setEnabled(int slotId, boolean enabled) {
        onMain(() -> toggleOnMain(slotId, enabled));
    }
    public void clear(int slotId) { onMain(() -> clearOnMain(slotId)); }
    public void clearAll() { onMain(this::clearAllOnMain); }

    private void saveOnMain(int slotId, String pin, String confirmation) {
        if (!acceptSlot(slotId) || slots[slotId].busy()) return;
        if (pin == null || !pin.matches("[0-9]{4,8}")) {
            callbacks.beginAction(slotId);
            showError(slotId, text(R.string.sim_error_pin_format));
            publish();
            return;
        }
        if (!pin.equals(confirmation)) {
            callbacks.beginAction(slotId);
            showError(slotId, text(R.string.sim_error_pin_mismatch));
            publish();
            return;
        }
        SlotCallbackGate.ActionToken token = beginAction(slotId);
        if (SaveRoutingPolicy.initial(availability[slotId]) == SaveRoutingPolicy.Route.LOCAL_STAGE) {
            stageLocally(slotId, pin);
            finish(slotId);
            return;
        }
        bridge.configure(slotId, pin, result -> onMain(() -> callbacks.runIfCurrent(token, () -> {
            handleSaveResult(slotId, pin, result);
            finish(slotId);
        })));
    }

    private void toggleOnMain(int slotId, boolean enabled) {
        if (!acceptSlot(slotId) || slots[slotId].busy()) return;
        if (availability[slotId] != SaveRoutingPolicy.Availability.AVAILABLE) {
            pageMessage = text(R.string.sim_error_module_unavailable);
            publish();
            return;
        }
        SlotCallbackGate.ActionToken token = beginAction(slotId);
        bridge.setEnabled(slotId, enabled, result -> onMain(() -> callbacks.runIfCurrent(token, () -> {
            if (result.resultCode() == ConfigContract.RESULT_MODULE_UNAVAILABLE) {
                availability[slotId] = SaveRoutingPolicy.Availability.UNAVAILABLE;
                renderSlot(slotId, null);
                showUnavailable();
            } else {
                availability[slotId] = SaveRoutingPolicy.Availability.AVAILABLE;
                if (result.resultCode() == ConfigContract.RESULT_OK) {
                    querySlot(slotId);
                } else {
                    showError(slotId, messageFor(result.resultCode()));
                }
            }
            finish(slotId);
        })));
    }

    private void clearOnMain(int slotId) {
        if (!acceptSlot(slotId) || slots[slotId].busy()) return;
        SlotCallbackGate.ActionToken token = beginAction(slotId);
        if (availability[slotId] != SaveRoutingPolicy.Availability.AVAILABLE) {
            try {
                pendingRepository.clear(slotId);
                renderKnownPending(slotId, PendingPinState.EMPTY);
                showNotice(slotId, text(R.string.sim_notice_clear_slot_local_only));
            } catch (RuntimeException failure) {
                showError(slotId, text(R.string.sim_error_local_storage));
            }
            finish(slotId);
            return;
        }
        bridge.clearSlot(slotId, result -> onMain(() -> callbacks.runIfCurrent(token, () -> {
            if (result.resultCode() == ConfigContract.RESULT_MODULE_UNAVAILABLE) {
                availability[slotId] = SaveRoutingPolicy.Availability.UNAVAILABLE;
                renderSlot(slotId, null);
                showUnavailable();
            } else {
                availability[slotId] = SaveRoutingPolicy.Availability.AVAILABLE;
                if (result.resultCode() != ConfigContract.RESULT_OK) {
                    showError(slotId, messageFor(result.resultCode()));
                } else {
                    clearPendingAndQuery(slotId);
                }
            }
            finish(slotId);
        })));
    }

    private void clearAllOnMain() {
        if (slots[0].busy() || slots[1].busy()) return;
        SlotCallbackGate.ActionToken first = callbacks.beginAction(0);
        SlotCallbackGate.ActionToken second = callbacks.beginAction(1);
        setBusy(0, true);
        setBusy(1, true);
        pageMessage = "";
        publish();
        boolean phoneKnownAvailable = availability[0] == SaveRoutingPolicy.Availability.AVAILABLE
                || availability[1] == SaveRoutingPolicy.Availability.AVAILABLE;
        boolean localCleared;
        try {
            pendingRepository.clearAll();
            localCleared = true;
        } catch (RuntimeException failure) {
            localCleared = false;
        }
        if (!phoneKnownAvailable) {
            if (localCleared) {
                renderKnownPending(0, PendingPinState.EMPTY);
                renderKnownPending(1, PendingPinState.EMPTY);
                pageMessage = text(R.string.sim_notice_clear_all_local_only);
            } else {
                showBothErrors(text(R.string.sim_error_local_storage));
            }
            finishAll();
            return;
        }
        boolean cleared = localCleared;
        bridge.clearAll(result -> onMain(() -> callbacks.runIfCurrent(first, second, () -> {
            handleClearAllResult(cleared, result);
            finishAll();
        })));
    }

    private void refreshOnMain() {
        pageMessage = "";
        for (int slotId = 0; slotId < 2; slotId++) {
            // A refresh must neither supersede an accepted write nor query an intermediate state.
            if (!slots[slotId].busy()) querySlot(slotId);
        }
        publish();
    }

    private void querySlot(int slotId) {
        Slot previous = slots[slotId];
        slots[slotId] = new Slot(slotId, loading(), "", false, previous.busy());
        SlotCallbackGate.QueryToken token = callbacks.beginQuery(slotId);
        bridge.query(slotId, result -> onMain(() -> callbacks.runIfCurrent(token, () -> {
            if (result.resultCode() == ConfigContract.RESULT_OK && result.status() != null) {
                availability[slotId] = SaveRoutingPolicy.Availability.AVAILABLE;
                renderSlot(slotId, result.status());
            } else if (result.resultCode() == ConfigContract.RESULT_MODULE_UNAVAILABLE) {
                availability[slotId] = SaveRoutingPolicy.Availability.UNAVAILABLE;
                renderSlot(slotId, null);
                showUnavailable();
            } else {
                availability[slotId] = SaveRoutingPolicy.Availability.AVAILABLE;
                showError(slotId, messageFor(result.resultCode()));
            }
            publish();
        })));
    }

    private void handleSaveResult(int slotId, String pin, PhoneBridgeClient.Result result) {
        SaveRoutingPolicy.Route route = SaveRoutingPolicy.afterBridgeResult(result.resultCode());
        if (route == SaveRoutingPolicy.Route.LOCAL_STAGE) {
            availability[slotId] = SaveRoutingPolicy.Availability.UNAVAILABLE;
            showUnavailable();
            stageLocally(slotId, pin);
            return;
        }
        availability[slotId] = SaveRoutingPolicy.Availability.AVAILABLE;
        if (route == SaveRoutingPolicy.Route.REPORT_ERROR) {
            showError(slotId, messageFor(result.resultCode()));
            return;
        }
        clearPendingAndQuery(slotId);
    }

    private void clearPendingAndQuery(int slotId) {
        try {
            pendingRepository.clear(slotId);
            querySlot(slotId);
        } catch (RuntimeException failure) {
            showError(slotId, text(R.string.sim_error_local_storage));
        }
    }

    private void handleClearAllResult(boolean localCleared, PhoneBridgeClient.Result result) {
        boolean unavailable = result.resultCode() == ConfigContract.RESULT_MODULE_UNAVAILABLE;
        availability[0] = availability[1] = unavailable
                ? SaveRoutingPolicy.Availability.UNAVAILABLE : SaveRoutingPolicy.Availability.AVAILABLE;
        if (result.resultCode() == ConfigContract.RESULT_OK) {
            if (localCleared) {
                querySlot(0);
                querySlot(1);
            } else {
                showBothErrors(text(R.string.sim_error_local_storage));
            }
            return;
        }
        String phoneFailure = messageFor(result.resultCode());
        String message = localCleared
                ? context.getString(R.string.sim_error_clear_all_phone_after_local, phoneFailure)
                : text(R.string.sim_error_local_storage) + "\n" + phoneFailure;
        if (unavailable && localCleared) {
            renderKnownPending(0, PendingPinState.EMPTY);
            renderKnownPending(1, PendingPinState.EMPTY);
            pageMessage = message;
        } else {
            showBothErrors(message);
            if (unavailable) pageMessage = message;
        }
    }

    private void stageLocally(int slotId, String pin) {
        try {
            pendingRepository.stage(slotId, pin);
            renderKnownPending(slotId, PendingPinState.STAGED);
        } catch (RuntimeException failure) {
            showError(slotId, text(R.string.sim_error_local_storage));
        }
    }

    private void renderSlot(int slotId, SlotStatus phoneStatus) {
        try {
            render(slotId, SlotPresentation.create(availability[slotId], phoneStatus,
                    pendingRepository.status(slotId)));
        } catch (RuntimeException failure) {
            showError(slotId, text(R.string.sim_error_local_storage));
        }
    }
    private void renderKnownPending(int slotId, PendingPinState state) {
        render(slotId, SlotPresentation.create(availability[slotId], null,
                new PendingPinStatus(slotId, state)));
    }
    private void render(int slotId, SlotPresentation presentation) {
        slots[slotId] = new Slot(slotId, presentation, "", false, slots[slotId].busy());
    }
    private void showError(int slotId, String message) {
        SlotPresentation old = slots[slotId].presentation();
        // Keep the existing error policy: disable toggle/clear until a fresh authoritative status.
        SlotPresentation disabled = new SlotPresentation(old.displayText(), false, false, false,
                old.pendingWarningVisible());
        slots[slotId] = new Slot(slotId, disabled, message, true, slots[slotId].busy());
    }
    private void showNotice(int slotId, String message) {
        slots[slotId] = new Slot(slotId, slots[slotId].presentation(), message, false, slots[slotId].busy());
    }
    private void showBothErrors(String message) { showError(0, message); showError(1, message); }
    private void showUnavailable() { pageMessage = text(R.string.sim_error_module_unavailable); }
    private SlotCallbackGate.ActionToken beginAction(int slotId) {
        SlotCallbackGate.ActionToken token = callbacks.beginAction(slotId);
        slots[slotId] = new Slot(slotId, slots[slotId].presentation(), "", false, true);
        pageMessage = "";
        publish();
        return token;
    }
    private void setBusy(int slotId, boolean busy) {
        Slot old = slots[slotId];
        slots[slotId] = new Slot(slotId, old.presentation(), old.message(), old.error(), busy);
    }
    private void finish(int slotId) { setBusy(slotId, false); publish(); }
    private void finishAll() { setBusy(0, false); setBusy(1, false); publish(); }
    private boolean acceptSlot(int slotId) {
        if (slotId == 0 || slotId == 1) return true;
        pageMessage = text(R.string.sim_error_invalid_request);
        publish();
        return false;
    }
    private SlotPresentation loading() {
        return new SlotPresentation(text(R.string.sim_status_loading), false, false, false, false);
    }
    private String text(int resource) { return context.getString(resource); }
    private String messageFor(int resultCode) {
        return text(switch (resultCode) {
            case ConfigContract.RESULT_INVALID_REQUEST -> R.string.sim_error_invalid_request;
            case ConfigContract.RESULT_SIM_UNAVAILABLE -> R.string.sim_error_sim_unavailable;
            case ConfigContract.RESULT_STORAGE_ERROR -> R.string.sim_error_storage;
            case ConfigContract.RESULT_MODULE_UNAVAILABLE -> R.string.sim_error_module_unavailable;
            default -> R.string.sim_error_unknown;
        });
    }
    private void onMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }
    private void publish() {
        Snapshot snapshot = new Snapshot(List.of(slots[0], slots[1]), pageMessage);
        current = snapshot;
        for (Observer observer : List.copyOf(observers)) notifyObserver(observer, snapshot);
    }
    private static void notifyObserver(Observer observer, Snapshot snapshot) {
        try {
            observer.onChanged(snapshot);
        } catch (RuntimeException ignored) {
            // A closed frontend must never abort another frontend's accepted operation.
        }
    }
    private static Bridge adapt(PhoneBridgeClient client) {
        return new Bridge() {
            @Override public void query(int slotId, PhoneBridgeClient.Callback cb) { client.query(slotId, cb); }
            @Override public void configure(int slotId, String pin, PhoneBridgeClient.Callback cb) { client.configure(slotId, pin, cb); }
            @Override public void setEnabled(int slotId, boolean enabled, PhoneBridgeClient.Callback cb) { client.setEnabled(slotId, enabled, cb); }
            @Override public void clearSlot(int slotId, PhoneBridgeClient.Callback cb) { client.clearSlot(slotId, cb); }
            @Override public void clearAll(PhoneBridgeClient.Callback cb) { client.clearAll(cb); }
        };
    }
}
