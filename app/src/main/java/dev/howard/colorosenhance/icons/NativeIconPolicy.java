package dev.howard.colorosenhance.icons;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

/** References the OEM collections so changes take effect without copying or writing them. */
record NativeIconPolicy(List<Collection<?>> blocked, boolean known) {
    static NativeIconPolicy read(Object manager, Object group) {
        try {
            Object controller = IconReflection.field(manager, "mController");
            var lists = new ArrayList<Collection<?>>();
            lists.add((Collection<?>) IconReflection.field(controller, "mIconHideList"));
            // ColorOS 16 stores policy exclusions on IconManager; 17 moved them to the controller.
            try { lists.add((Collection<?>) IconReflection.field(controller, "mIconHidePolicyList")); }
            catch (NoSuchFieldException older) { lists.add((Collection<?>) IconReflection.field(manager, "mPolicyBlockList")); }
            lists.add((Collection<?>) IconReflection.field(manager, "mBlockList"));
            lists.add((Collection<?>) IconReflection.field(group, "mIgnoredSlots"));
            if (lists.stream().anyMatch(java.util.Objects::isNull)) return new NativeIconPolicy(List.of(), false);
            return new NativeIconPolicy(List.copyOf(lists), true);
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return new NativeIconPolicy(List.of(), false); }
    }
    boolean allows(String slot) { return known && blocked.stream().noneMatch(set -> set.contains(slot)); }
    // read() keeps the controller's live user hide list first, separate from per-host exclusions.
    boolean userHidden(String slot) { return known && !blocked.isEmpty() && blocked.get(0).contains(slot); }
}
