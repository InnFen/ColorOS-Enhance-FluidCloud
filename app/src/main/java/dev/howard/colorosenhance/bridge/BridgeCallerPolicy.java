package dev.howard.colorosenhance.bridge;
public final class BridgeCallerPolicy {
    public static boolean allow(int caller,int own,int settings,boolean platformSigned) {
        if (own < 0 || own >= 100000 || caller < 0 || caller >= 100000) return false;
        return caller == own || (caller == 1000 && settings == 1000 && platformSigned);
    }
}
