package dev.howard.colorosenhance.icons;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Network availability and the system user's hidden icons always remain authoritative. */
public record StatusIconConfig(boolean enabled, int bluetoothMode, Set<String> shadeOnly, boolean forceHdHome,
                               boolean keyguardFollow,boolean orderEnabled,IconOrder order) {
    public static final String GROUP = "icons", PREFIX = "shade_only.";
    public static final String[] BLUETOOTH_LABELS = {"跟随系统", "仅连接时可见", "仅连接耳机或有电量的设备时可见"};
    public StatusIconConfig { shadeOnly = Set.copyOf(shadeOnly);java.util.Objects.requireNonNull(order); }
    public StatusIconConfig(boolean enabled,int bluetoothMode,Set<String> shadeOnly,boolean forceHdHome,boolean keyguardFollow){
        this(enabled,bluetoothMode,shadeOnly,forceHdHome,keyguardFollow,false,IconOrder.EMPTY);
    }
    public static boolean validSlot(String slot) {
        return slot != null && slot.matches("[a-zA-Z0-9_][a-zA-Z0-9_.:-]{0,95}");
    }
    public static boolean validValue(String key, Object value) {
        if ("enabled".equals(key) || "force_hd_home".equals(key) || "keyguard_follow".equals(key) || "order_enabled".equals(key)) return value instanceof Boolean;
        if (IconOrder.KEY.equals(key))return value instanceof String text && IconOrder.valid(text);
        if ("bluetooth_mode".equals(key)) return value instanceof Integer n && n >= 0 && n <= 2;
        return key != null && key.startsWith(PREFIX) && validSlot(key.substring(PREFIX.length())) && value instanceof Boolean;
    }
    public static StatusIconConfig from(Map<String, ?> values) {
        int mode = values.get("bluetooth_mode") instanceof Integer n && n >= 0 && n <= 2 ? n : 0;
        Set<String> selected = new HashSet<>();
        values.forEach((key, value) -> {
            if (key.startsWith(PREFIX) && Boolean.TRUE.equals(value) && validSlot(key.substring(PREFIX.length())))
                selected.add(key.substring(PREFIX.length()));
        });
        IconOrder order=IconOrder.EMPTY;
        try{if(values.get(IconOrder.KEY) instanceof String text)order=IconOrder.decode(text);}catch(IllegalArgumentException ignored){/* Native defaults. */}
        return new StatusIconConfig(Boolean.TRUE.equals(values.get("enabled")), mode, selected,
                Boolean.TRUE.equals(values.get("force_hd_home")),!Boolean.FALSE.equals(values.get("keyguard_follow")),
                Boolean.TRUE.equals(values.get("order_enabled")),order);
    }
    public boolean ordersHome(){return enabled && orderEnabled;}
    public boolean appliesTo(String location) {
        return enabled && (Set.of("HOME","QS","SHADE_CARRIER_GROUP").contains(location==null?"":location)
                || (keyguardFollow && "KEYGUARD".equals(location)));
    }
    public boolean restricts(String slot, String location, BluetoothState bluetooth) {
        if (!appliesTo(location)) return false;
        if (("HOME".equals(location) || "KEYGUARD".equals(location)) && shadeOnly.contains(slot)) return true;
        return restrictsBluetooth(slot, location, bluetooth);
    }
    public boolean restrictsBluetooth(String slot, String location, BluetoothState bluetooth) {
        if (!appliesTo(location)) return false;
        if (!"bluetooth".equals(slot) || bluetoothMode == 0 || !bluetooth.known()) return false;
        if (!bluetooth.connected()) return true;
        return bluetoothMode == 2 && Boolean.FALSE.equals(bluetooth.qualified());
    }
    public record BluetoothState(boolean known, boolean connected, Boolean qualified) {
        public static BluetoothState unknown() { return new BluetoothState(false, false, null); }
    }
}
