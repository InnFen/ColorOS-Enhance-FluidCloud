package dev.howard.colorosenhance.icons;

import java.lang.reflect.Method;
import java.util.Collection;

/** Reads cached SystemUI state. Never pairs, connects, or queries device addresses/names. */
final class BluetoothStateReader {
    static StatusIconConfig.BluetoothState read(Object policy) {
        try {
            boolean connected = (Boolean) IconReflection.field(policy, "bluetoothConnected");
            if (!connected) return new StatusIconConfig.BluetoothState(true, false, false);
            Object controller = IconReflection.field(policy, "bluetoothController");
            Object devices = IconReflection.call(controller, "getConnectedDevices");
            if (!(devices instanceof Collection<?> list)) return new StatusIconConfig.BluetoothState(true, true, null);
            boolean unknown = false;
            for (Object cached : list) {
                try {
                    if (!Boolean.TRUE.equals(IconReflection.call(cached, "isConnected"))) continue;
                    if (qualifies(cached)) return new StatusIconConfig.BluetoothState(true, true, true);
                } catch (ReflectiveOperationException | RuntimeException unavailable) { unknown = true; }
            }
            return new StatusIconConfig.BluetoothState(true, true, unknown ? null : false);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return StatusIconConfig.BluetoothState.unknown(); }
    }
    private static boolean qualifies(Object cached) throws ReflectiveOperationException {
        if (battery(IconReflection.call(cached, "getBatteryLevel"))) return true;
        // Android 17 also exposes independently reported earbud/case battery levels.
        Method levels = IconReflection.optional(cached.getClass(), "getBatteryLevelsInfo");
        if (levels != null) {
            Object info = levels.invoke(cached);
            if (info != null) for (String part : new String[]{"getLeftBatteryLevel", "getRightBatteryLevel", "getCaseBatteryLevel", "getOverallBatteryLevel"})
                if (battery(IconReflection.call(info, part))) return true;
        }
        Object device = IconReflection.call(cached, "getDevice");
        Object classification = IconReflection.call(device, "getBluetoothClass");
        if (classification != null) {
            Object value = IconReflection.call(classification, "getDeviceClass");
            if (value instanceof Integer c && (c == 0x404 || c == 0x408 || c == 0x418)) return true;
        }
        for (String name : new String[]{"isConnectedHearingAidDevice", "isConnectedLeAudioDevice"}) {
            Method method = IconReflection.optional(cached.getClass(), name);
            if (method != null && Boolean.TRUE.equals(method.invoke(cached))) return true;
        }
        return false;
    }
    private static boolean battery(Object value) { return value instanceof Integer n && n >= 0 && n <= 100; }
}
