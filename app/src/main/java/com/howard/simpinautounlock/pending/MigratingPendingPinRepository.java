package com.howard.simpinautounlock.pending;

import java.util.Optional;

public final class MigratingPendingPinRepository implements PendingPinRepository {
    private final PendingPinRepository local;
    private PendingPinRepository remote;

    public MigratingPendingPinRepository(PendingPinRepository local) {
        this.local = local;
    }

    public synchronized void attachRemote(PendingPinRepository repository) {
        remote = repository;
        for (int slotId = 0; slotId < 2; slotId++) {
            migrateSlot(repository, slotId);
        }
    }

    public synchronized void detachRemote() {
        remote = null;
    }

    @Override
    public synchronized void stage(int slotId, String pin) {
        active().stage(slotId, pin);
        if (remote != null) {
            clearLocalBestEffort(slotId);
        }
    }

    @Override
    public synchronized PendingPinStatus status(int slotId) {
        return active().status(slotId);
    }

    @Override
    public synchronized Optional<Claim> claim(int slotId) {
        return active().claim(slotId);
    }

    @Override
    public synchronized boolean acknowledge(int slotId, String token) {
        return active().acknowledge(slotId, token);
    }

    @Override
    public synchronized void clear(int slotId) {
        RuntimeException failure = clearLocal(slotId, null);
        if (remote != null) {
            failure = clearRemote(slotId, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public synchronized void clearAll() {
        RuntimeException failure = clearAllLocal(null);
        if (remote != null) {
            try {
                remote.clearAll();
            } catch (RuntimeException error) {
                failure = first(failure, error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void migrateSlot(PendingPinRepository destination, int slotId) {
        if (local.status(slotId).state() != PendingPinState.STAGED) {
            return;
        }
        Optional<Claim> pending = local.claim(slotId);
        if (pending.isEmpty()) {
            return;
        }
        Claim claim = pending.get();
        try {
            destination.stage(slotId, claim.pin());
        } catch (RuntimeException error) {
            local.stage(slotId, claim.pin());
            throw error;
        }
        if (!local.acknowledge(slotId, claim.token())) {
            local.clear(slotId);
        }
    }

    private PendingPinRepository active() {
        return remote == null ? local : remote;
    }

    private void clearLocalBestEffort(int slotId) {
        try {
            local.clear(slotId);
        } catch (RuntimeException ignored) {
            // The remote staged record is authoritative once it has committed.
        }
    }

    private RuntimeException clearLocal(int slotId, RuntimeException failure) {
        try {
            local.clear(slotId);
        } catch (RuntimeException error) {
            return first(failure, error);
        }
        return failure;
    }

    private RuntimeException clearRemote(int slotId, RuntimeException failure) {
        try {
            remote.clear(slotId);
        } catch (RuntimeException error) {
            return first(failure, error);
        }
        return failure;
    }

    private RuntimeException clearAllLocal(RuntimeException failure) {
        try {
            local.clearAll();
        } catch (RuntimeException error) {
            return first(failure, error);
        }
        return failure;
    }

    private static RuntimeException first(RuntimeException first, RuntimeException next) {
        return first == null ? next : first;
    }
}
