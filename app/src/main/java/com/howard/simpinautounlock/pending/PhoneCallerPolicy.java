package com.howard.simpinautounlock.pending;

final class PhoneCallerPolicy {
    private PhoneCallerPolicy() {}

    static boolean allows(
            int callingUid,
            int resolvedPhoneUid,
            boolean hasReadPrivilegedPhoneState,
            boolean hasModifyPhoneState) {
        return resolvedPhoneUid >= 0
                && callingUid == resolvedPhoneUid
                && hasReadPrivilegedPhoneState
                && hasModifyPhoneState;
    }
}
