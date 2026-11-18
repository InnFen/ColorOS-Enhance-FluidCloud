package com.howard.simpinautounlock.storage;

import java.security.GeneralSecurityException;
import java.util.Optional;
import javax.crypto.SecretKey;

public interface KeyProvider {
    SecretKey getOrCreate() throws GeneralSecurityException;

    Optional<SecretKey> getIfPresent() throws GeneralSecurityException;

    void delete() throws GeneralSecurityException;
}
