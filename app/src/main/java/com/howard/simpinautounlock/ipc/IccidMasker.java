package com.howard.simpinautounlock.ipc;

public final class IccidMasker {
    private IccidMasker() {}

    public static String lastFour(String iccid) {
        if (iccid == null || iccid.length() < 4) {
            return "";
        }
        return iccid.substring(iccid.length() - 4);
    }
}
