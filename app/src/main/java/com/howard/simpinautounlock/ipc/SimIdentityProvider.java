package com.howard.simpinautounlock.ipc;

import java.util.Optional;

public interface SimIdentityProvider {
    Optional<String> currentIccid(int slotId);
}
