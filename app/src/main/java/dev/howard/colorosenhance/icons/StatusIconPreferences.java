package dev.howard.colorosenhance.icons;

import dev.howard.colorosenhance.PreferenceSession;
import java.util.HashMap;
import java.util.Map;

/** Canonicalizes only the app's mistaken legacy ringer label, never native system hide lists. */
public final class StatusIconPreferences {
    private StatusIconPreferences() {}
    public static Map<String,Object> migrateLegacy(Map<String,?> previous){
        var next=new HashMap<String,Object>(previous);
        if(next.get(IconOrder.KEY) instanceof String text)try{
            IconOrder old=IconOrder.decode(text),canonical=old.ringerSlot();
            if(!old.equals(canonical))next.put(IconOrder.KEY,canonical.encode());
        }
        catch(IllegalArgumentException invalid){/* Preserve unknown future layouts. */}
        Object legacy=next.remove(StatusIconConfig.PREFIX+"mute");
        if(Boolean.TRUE.equals(legacy))next.put(StatusIconConfig.PREFIX+"volume",true);
        else if(legacy instanceof Boolean && !next.containsKey(StatusIconConfig.PREFIX+"volume"))next.put(StatusIconConfig.PREFIX+"volume",false);
        return next;
    }
    public static void initialize(PreferenceSession.Store store){
        var previous=store.read();var next=migrateLegacy(previous);
        if(!previous.equals(next) && !store.replace(next))throw new IllegalStateException("Status icon slot migration unconfirmed");
    }
}
