package dev.howard.colorosenhance.bridge;
import android.os.IBinder;
public final class BridgeContract {
    public static final String PACKAGE = dev.howard.colorosenhance.BuildConfig.APPLICATION_ID;
    public static final String SERVICE = "dev.howard.colorosenhance.bridge.SettingsBridgeService";
    public static final String DESCRIPTOR = PACKAGE + ".SettingsBridge.v1";
    public static final int REQUEST = IBinder.FIRST_CALL_TRANSACTION;
    private BridgeContract() {}
}
