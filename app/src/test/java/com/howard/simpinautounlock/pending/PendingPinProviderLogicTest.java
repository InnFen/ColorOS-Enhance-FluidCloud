package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public final class PendingPinProviderLogicTest {
    private RecordingRepository repository;
    private PendingPinProviderLogic logic;

    @Before
    public void setUp() {
        repository = new RecordingRepository();
        logic = new PendingPinProviderLogic(repository);
    }

    @Test
    public void claimsEachSupportedSlot() {
        repository.claims.put(0, new PendingPinRepository.Claim("1234", "token-0"));
        repository.claims.put(1, new PendingPinRepository.Claim("5678", "token-1"));

        assertEquals(
                new PendingPinProviderLogic.Response(
                        PendingPinProviderLogic.Outcome.SUCCESS, "1234", "token-0"),
                logic.invoke(PendingPinContract.METHOD_CLAIM, 0, null));
        assertEquals(
                new PendingPinProviderLogic.Response(
                        PendingPinProviderLogic.Outcome.SUCCESS, "5678", "token-1"),
                logic.invoke(PendingPinContract.METHOD_CLAIM, 1, null));
        assertEquals(2, repository.callCount);
    }

    @Test
    public void missingRecordReturnsEmpty() {
        assertEquals(
                emptyResponse(PendingPinProviderLogic.Outcome.EMPTY),
                logic.invoke(PendingPinContract.METHOD_CLAIM, 0, null));
        assertEquals(1, repository.callCount);
    }

    @Test
    public void nonIntegerSlotNeverReachesRepository() {
        assertEquals(
                emptyResponse(PendingPinProviderLogic.Outcome.INVALID),
                logic.invoke(PendingPinContract.METHOD_CLAIM, "0", null));
        assertEquals(0, repository.callCount);
    }

    @Test
    public void invalidSlotsNeverReachRepository() {
        for (int slotId : new int[] {-1, 2}) {
            assertEquals(
                    emptyResponse(PendingPinProviderLogic.Outcome.INVALID),
                    logic.invoke(PendingPinContract.METHOD_CLAIM, slotId, null));
        }
        assertEquals(0, repository.callCount);
    }

    @Test
    public void unknownMethodNeverReachesRepository() {
        assertEquals(
                emptyResponse(PendingPinProviderLogic.Outcome.INVALID),
                logic.invoke("unknown", 0, "token"));
        assertEquals(0, repository.callCount);
    }

    @Test
    public void nonStringAcknowledgementTokenNeverReachesRepository() {
        for (Object token : new Object[] {null, 7}) {
            assertEquals(
                    emptyResponse(PendingPinProviderLogic.Outcome.INVALID),
                    logic.invoke(PendingPinContract.METHOD_ACK, 0, token));
        }
        assertEquals(0, repository.callCount);
    }

    @Test
    public void exactAcknowledgementReturnsSuccess() {
        repository.acknowledgement = true;

        assertEquals(
                emptyResponse(PendingPinProviderLogic.Outcome.SUCCESS),
                logic.invoke(PendingPinContract.METHOD_ACK, 1, "exact-token"));
        assertEquals(1, repository.callCount);
        assertEquals(1, repository.acknowledgedSlot);
        assertEquals("exact-token", repository.acknowledgedToken);
    }

    @Test
    public void staleAcknowledgementReturnsEmpty() {
        assertEquals(
                emptyResponse(PendingPinProviderLogic.Outcome.EMPTY),
                logic.invoke(PendingPinContract.METHOD_ACK, 0, "stale-token"));
        assertEquals(1, repository.callCount);
    }

    @Test
    public void claimRepositoryExceptionReturnsUnavailableWithoutDetails() {
        repository.claimFailure = new IllegalStateException("sensitive claim detail");

        PendingPinProviderLogic.Response response =
                logic.invoke(PendingPinContract.METHOD_CLAIM, 0, null);

        assertEquals(emptyResponse(PendingPinProviderLogic.Outcome.UNAVAILABLE), response);
        assertFalse(response.toString().contains("sensitive claim detail"));
    }

    @Test
    public void acknowledgementRepositoryExceptionReturnsUnavailableWithoutDetails() {
        repository.acknowledgeFailure =
                new IllegalStateException("sensitive acknowledgement detail");

        PendingPinProviderLogic.Response response =
                logic.invoke(PendingPinContract.METHOD_ACK, 0, "token");

        assertEquals(emptyResponse(PendingPinProviderLogic.Outcome.UNAVAILABLE), response);
        assertFalse(response.toString().contains("sensitive acknowledgement detail"));
    }

    private static PendingPinProviderLogic.Response emptyResponse(
            PendingPinProviderLogic.Outcome outcome) {
        return new PendingPinProviderLogic.Response(outcome, null, null);
    }

    private static final class RecordingRepository implements PendingPinRepository {
        private final Map<Integer, Claim> claims = new HashMap<>();
        private int callCount;
        private boolean acknowledgement;
        private int acknowledgedSlot = -1;
        private String acknowledgedToken;
        private RuntimeException claimFailure;
        private RuntimeException acknowledgeFailure;

        @Override
        public void stage(int slotId, String pin) {
            callCount++;
        }

        @Override
        public PendingPinStatus status(int slotId) {
            callCount++;
            return new PendingPinStatus(slotId, PendingPinState.EMPTY);
        }

        @Override
        public Optional<Claim> claim(int slotId) {
            callCount++;
            if (claimFailure != null) {
                throw claimFailure;
            }
            return Optional.ofNullable(claims.get(slotId));
        }

        @Override
        public boolean acknowledge(int slotId, String token) {
            callCount++;
            acknowledgedSlot = slotId;
            acknowledgedToken = token;
            if (acknowledgeFailure != null) {
                throw acknowledgeFailure;
            }
            return acknowledgement;
        }

        @Override
        public void clear(int slotId) {
            callCount++;
        }

        @Override
        public void clearAll() {
            callCount++;
        }
    }
}
