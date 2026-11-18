package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import org.junit.Test;

public final class RepositoryPendingPinSourceTest {
    @Test
    public void mapsRepositoryClaimAndAcknowledgement() {
        RecordingRepository repository = new RecordingRepository();
        repository.claim = new PendingPinRepository.Claim("1234", "token-0");
        RepositoryPendingPinSource source = new RepositoryPendingPinSource(repository);

        assertEquals(
                new com.howard.simpinautounlock.core.PendingPinSource.Claim(
                        "1234", "token-0"),
                source.claim(0).orElseThrow());
        assertFalse(source.acknowledge(0, "remote-read-only"));
        assertNull(repository.acknowledgedToken);
    }

    @Test
    public void unavailableRemoteReadBlocksOfficialFallback() {
        RecordingRepository repository = new RecordingRepository();
        repository.peekFailure = new IllegalStateException("corrupt remote record");
        RepositoryPendingPinSource source = new RepositoryPendingPinSource(repository);

        com.howard.simpinautounlock.core.PendingPinSource.Resolution resolution =
                source.resolve(0);

        assertTrue(resolution.claim().isEmpty());
        assertFalse(resolution.allowFallback());
    }

    private static final class RecordingRepository implements PendingPinRepository {
        private Claim claim;
        private String acknowledgedToken;
        private RuntimeException peekFailure;

        @Override public void stage(int slotId, String pin) {}
        @Override public PendingPinStatus status(int slotId) {
            return new PendingPinStatus(slotId, PendingPinState.EMPTY);
        }
        @Override public Optional<Claim> claim(int slotId) {
            throw new AssertionError("hooked process must not mutate remote preferences");
        }
        @Override public Optional<Claim> peek(int slotId) {
            if (peekFailure != null) throw peekFailure;
            return Optional.ofNullable(claim);
        }
        @Override public boolean acknowledge(int slotId, String token) {
            acknowledgedToken = token;
            return true;
        }
        @Override public void clear(int slotId) {}
        @Override public void clearAll() {}
    }
}
