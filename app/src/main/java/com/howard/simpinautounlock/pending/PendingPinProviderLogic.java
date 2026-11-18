package com.howard.simpinautounlock.pending;

import java.util.Optional;

final class PendingPinProviderLogic {
    enum Outcome {
        SUCCESS,
        EMPTY,
        INVALID,
        UNAVAILABLE
    }

    record Response(Outcome outcome, String pin, String claimToken) {}

    private final PendingPinRepository repository;

    PendingPinProviderLogic(PendingPinRepository repository) {
        this.repository = repository;
    }

    Response invoke(String method, Object slotValue, Object tokenValue) {
        if (!PendingPinContract.METHOD_CLAIM.equals(method)
                && !PendingPinContract.METHOD_ACK.equals(method)) {
            return response(Outcome.INVALID);
        }
        if (!(slotValue instanceof Integer slotId) || slotId < 0 || slotId > 1) {
            return response(Outcome.INVALID);
        }
        if (PendingPinContract.METHOD_ACK.equals(method)
                && !(tokenValue instanceof String)) {
            return response(Outcome.INVALID);
        }

        try {
            if (PendingPinContract.METHOD_CLAIM.equals(method)) {
                Optional<PendingPinRepository.Claim> claim = repository.claim(slotId);
                return claim.map(value -> new Response(
                                Outcome.SUCCESS, value.pin(), value.token()))
                        .orElseGet(() -> response(Outcome.EMPTY));
            }
            return repository.acknowledge(slotId, (String) tokenValue)
                    ? response(Outcome.SUCCESS)
                    : response(Outcome.EMPTY);
        } catch (RuntimeException error) {
            return response(Outcome.UNAVAILABLE);
        }
    }

    private static Response response(Outcome outcome) {
        return new Response(outcome, null, null);
    }
}
