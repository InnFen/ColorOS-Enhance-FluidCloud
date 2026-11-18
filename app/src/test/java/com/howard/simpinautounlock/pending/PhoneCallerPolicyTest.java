package com.howard.simpinautounlock.pending;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PhoneCallerPolicyTest {
    private static final int PHONE_UID = 10123;

    @Test
    public void allowsExactPhoneUidWithBothPermissions() {
        assertTrue(PhoneCallerPolicy.allows(PHONE_UID, PHONE_UID, true, true));
    }

    @Test
    public void rejectsWrongUid() {
        assertFalse(PhoneCallerPolicy.allows(PHONE_UID + 1, PHONE_UID, true, true));
    }

    @Test
    public void rejectsCallerMissingReadPrivilegedPhoneState() {
        assertFalse(PhoneCallerPolicy.allows(PHONE_UID, PHONE_UID, false, true));
    }

    @Test
    public void rejectsCallerMissingModifyPhoneState() {
        assertFalse(PhoneCallerPolicy.allows(PHONE_UID, PHONE_UID, true, false));
    }

    @Test
    public void rejectsCallerMissingBothPermissions() {
        assertFalse(PhoneCallerPolicy.allows(PHONE_UID, PHONE_UID, false, false));
    }

    @Test
    public void rejectsNegativeUnresolvedPhoneUidEvenWhenItMatchesCaller() {
        assertFalse(PhoneCallerPolicy.allows(-1, -1, true, true));
    }
}
