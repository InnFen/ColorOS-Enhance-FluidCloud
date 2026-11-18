package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.PendingPinSource;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

public final class ConsumingPendingPinSourceTest {
    @Test
    public void returnsEachGenerationOnlyOnceAcrossSourceInstances() {
        MutableSource remote = new MutableSource();
        remote.claim = new PendingPinSource.Claim("1234", "generation-1");
        Set<String> consumed = new HashSet<>();
        PendingPinGenerationGate gate =
                (slotId, generation) -> consumed.add(slotId + ":" + generation)
                        ? PendingPinGenerationGate.Result.ACCEPTED
                        : PendingPinGenerationGate.Result.IN_PROGRESS;

        ConsumingPendingPinSource first = new ConsumingPendingPinSource(remote, gate);
        ConsumingPendingPinSource second = new ConsumingPendingPinSource(remote, gate);

        assertTrue(first.claim(0).isPresent());
        assertFalse(second.claim(0).isPresent());
        remote.claim = new PendingPinSource.Claim("5678", "generation-2");
        assertEquals("5678", second.claim(0).orElseThrow().pin());
    }

    @Test
    public void unavailableGateReturnsNoPinAndBlocksFallback() {
        MutableSource remote = new MutableSource();
        remote.claim = new PendingPinSource.Claim("1234", "generation-1");
        ConsumingPendingPinSource source =
                new ConsumingPendingPinSource(
                        remote,
                        (slotId, generation) -> PendingPinGenerationGate.Result.UNAVAILABLE);

        PendingPinSource.Resolution resolution = source.resolve(0);
        assertTrue(resolution.claim().isEmpty());
        assertFalse(resolution.allowFallback());
    }

    private static final class MutableSource implements PendingPinSource {
        private Claim claim;

        @Override
        public Optional<Claim> claim(int slotId) {
            return Optional.ofNullable(claim);
        }

        @Override
        public boolean acknowledge(int slotId, String token) {
            throw new AssertionError("remote preferences are read-only in hooked processes");
        }
    }
}
