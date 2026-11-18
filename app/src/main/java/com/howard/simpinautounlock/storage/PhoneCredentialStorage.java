package com.howard.simpinautounlock.storage;

/** Stable phone-UID storage for this product, including its application-ID rename.
 * Not the storage of the separate historical SIM module. Do not rename or copy:
 * keeping these IDs preserves encrypted records and the per-boot attempt guard. */
public final class PhoneCredentialStorage {
    public static final String PREFERENCES = "dev.howard.colorosenhance.sim";
    public static final String KEY_ALIAS = "dev.howard.colorosenhance.sim.aes.v1";
    public static final String CONSUMPTION = "dev.howard.colorosenhance.sim_pending_consumption";
    private PhoneCredentialStorage() { }
}
