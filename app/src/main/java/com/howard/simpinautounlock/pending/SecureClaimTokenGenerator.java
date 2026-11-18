package com.howard.simpinautounlock.pending;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecureClaimTokenGenerator implements ClaimTokenGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String nextToken() {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
