package com.howard.simpinautounlock.core;

import java.util.Optional;

public interface CredentialRepository {
    Optional<SlotRecord> load(int slotId);

    void save(SlotRecord record);

    void clear(int slotId);

    void clearAll();
}
