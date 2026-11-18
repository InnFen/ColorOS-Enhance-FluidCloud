package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Optional;
import org.junit.Test;

public final class MigratingPendingPinRepositoryTest {
    @Test
    public void attachRemoteMigratesExistingLocalStage() {
        MemoryRepository local = new MemoryRepository();
        MemoryRepository remote = new MemoryRepository();
        local.stage(0, "1234");
        MigratingPendingPinRepository repository =
                new MigratingPendingPinRepository(local);

        repository.attachRemote(remote);

        assertEquals(PendingPinState.EMPTY, local.status(0).state());
        assertEquals(PendingPinState.STAGED, remote.status(0).state());
        assertEquals("1234", remote.claim(0).orElseThrow().pin());
    }

    @Test
    public void stagesIntoRemoteAfterItIsAttached() {
        MemoryRepository local = new MemoryRepository();
        MemoryRepository remote = new MemoryRepository();
        MigratingPendingPinRepository repository =
                new MigratingPendingPinRepository(local);
        repository.attachRemote(remote);

        repository.stage(1, "5678");

        assertEquals(PendingPinState.EMPTY, local.status(1).state());
        assertEquals(PendingPinState.STAGED, remote.status(1).state());
    }

    @Test
    public void failedRemoteMigrationRestoresLocalStage() {
        MemoryRepository local = new MemoryRepository();
        MemoryRepository remote = new MemoryRepository();
        local.stage(0, "1234");
        remote.failStage = true;
        MigratingPendingPinRepository repository =
                new MigratingPendingPinRepository(local);

        assertThrows(IllegalStateException.class, () -> repository.attachRemote(remote));

        assertEquals(PendingPinState.STAGED, local.status(0).state());
    }

    @Test
    public void partialDualSlotMigrationRemainsReachableForClearAll() {
        MemoryRepository local = new MemoryRepository();
        MemoryRepository remote = new MemoryRepository();
        local.stage(0, "1234");
        local.stage(1, "5678");
        remote.failStageSlot = 1;
        MigratingPendingPinRepository repository =
                new MigratingPendingPinRepository(local);

        assertThrows(IllegalStateException.class, () -> repository.attachRemote(remote));
        repository.clearAll();

        assertEquals(PendingPinState.EMPTY, local.status(0).state());
        assertEquals(PendingPinState.EMPTY, local.status(1).state());
        assertEquals(PendingPinState.EMPTY, remote.status(0).state());
        assertEquals(PendingPinState.EMPTY, remote.status(1).state());
    }

    private static final class MemoryRepository implements PendingPinRepository {
        private final String[] pins = new String[2];
        private final PendingPinState[] states = {
                PendingPinState.EMPTY, PendingPinState.EMPTY
        };
        private boolean failStage;
        private int failStageSlot = -1;

        @Override
        public void stage(int slotId, String pin) {
            if (failStage || failStageSlot == slotId) {
                throw new IllegalStateException("stage failed");
            }
            pins[slotId] = pin;
            states[slotId] = PendingPinState.STAGED;
        }

        @Override
        public PendingPinStatus status(int slotId) {
            return new PendingPinStatus(slotId, states[slotId]);
        }

        @Override
        public Optional<Claim> claim(int slotId) {
            if (states[slotId] != PendingPinState.STAGED) {
                return Optional.empty();
            }
            states[slotId] = PendingPinState.CLAIMED;
            return Optional.of(new Claim(pins[slotId], "token-" + slotId));
        }

        @Override
        public boolean acknowledge(int slotId, String token) {
            if (states[slotId] != PendingPinState.CLAIMED
                    || !("token-" + slotId).equals(token)) {
                return false;
            }
            clear(slotId);
            return true;
        }

        @Override
        public void clear(int slotId) {
            pins[slotId] = null;
            states[slotId] = PendingPinState.EMPTY;
        }

        @Override
        public void clearAll() {
            clear(0);
            clear(1);
        }
    }
}
