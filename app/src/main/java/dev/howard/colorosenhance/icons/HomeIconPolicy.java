package dev.howard.colorosenhance.icons;

import java.util.List;

/** Only removes the HOME default exclusion; never changes the global user blocklist or IMS state. */
final class HomeIconPolicy {
    static List<String> effective(List<String> nativeSlots, String location, StatusIconConfig config) {
        if (!config.enabled() || !config.forceHdHome() || !"HOME".equals(location)) return nativeSlots;
        return nativeSlots.stream().filter(slot -> !"volte".equals(slot)).toList();
    }
    private HomeIconPolicy() {}
}
