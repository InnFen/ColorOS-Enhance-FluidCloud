package dev.howard.colorosenhance;

import java.util.Map;
import java.util.Objects;

/**
 * Stateless presentation commands. The coordinator remains the only configuration owner.
 * Delayed inputs carry their original revision so a rebind or another frontend cannot be overwritten.
 */
public final class SettingsUiController {
    private static final SettingsCoordinator.State DISCONNECTED=
            new SettingsCoordinator.State(-1,Map.of(),false);
    private final SettingsCoordinator coordinator;

    public SettingsUiController(SettingsCoordinator coordinator){this.coordinator=Objects.requireNonNull(coordinator);}

    public SettingsCoordinator.State state(String group){
        SettingsCoordinator.State current=coordinator.read(group);
        return current==null?DISCONNECTED:current;
    }

    public String toggle(String group,String key,boolean checked){
        if(!SettingsCoordinator.validValue(group,key,checked))return "invalid";
        SettingsCoordinator.State current=state(group);
        String unavailable=unavailable(current);if(unavailable!=null)return unavailable;
        if(!canEditFlag(group,key,current.values()))return "invalid";
        return coordinator.write(group,key,checked,current.revision()).status();
    }

    public String number(String group,SettingsCatalog.Item item,int value,long expectedRevision){
        SettingsCatalog.Item canonical=numberItem(group,item);
        if(canonical==null || !SettingsCoordinator.validValue(group,canonical.valueKey(),value))return "invalid";
        SettingsCoordinator.State current=state(group);
        String rejection=preflight(current,expectedRevision);if(rejection!=null)return rejection;
        if(!canEditNumber(group,canonical,current.values()))return "invalid";
        return coordinator.write(group,canonical.valueKey(),value,expectedRevision).status();
    }

    public String put(String group,String key,Object value,long revision){
        if(!SettingsCoordinator.validValue(group,key,value))return "invalid";
        SettingsCoordinator.State current=state(group);
        String rejection=preflight(current,revision);if(rejection!=null)return rejection;
        if("icons".equals(group) && "bluetooth_mode".equals(key)
                && !Boolean.TRUE.equals(current.values().get("enabled")))return "invalid";
        if(value instanceof Boolean && !canEditFlag(group,key,current.values()))return "invalid";
        SettingsCatalog.Group catalog=SettingsCatalog.find(group);
        if(value instanceof Integer && catalog!=null){
            for(SettingsCatalog.Item item:catalog.items())if(item.adjustable() && item.valueKey().equals(key)
                    && !canEditNumber(group,item,current.values()))return "invalid";
        }
        return coordinator.write(group,key,value,revision).status();
    }

    public String reset(String group,long revision){
        if(SettingsCatalog.find(group)==null || "sim".equals(group))return "invalid";
        String rejection=preflight(state(group),revision);if(rejection!=null)return rejection;
        return coordinator.reset(group,revision).status();
    }

    public String resetWave(long revision) {
        return coordinator.resetWave(revision).status();
    }

    private static SettingsCatalog.Item numberItem(String group,SettingsCatalog.Item supplied){
        SettingsCatalog.Group catalog=SettingsCatalog.find(group);
        if(supplied==null || catalog==null || !supplied.adjustable())return null;
        for(SettingsCatalog.Item item:catalog.items())if(item.adjustable() && item.key().equals(supplied.key())
                && item.valueKey().equals(supplied.valueKey()))return item;
        return null;
    }
    private static boolean canEditFlag(String group,String key,Map<String,Object> values){
        if("enabled".equals(key) || "integration".equals(group))return true;
        if(!SettingsCatalog.controlEnabled(group,key,values))return false;
        SettingsCatalog.Group catalog=SettingsCatalog.find(group);
        if(catalog!=null)for(SettingsCatalog.Item item:catalog.items())if(item.key().equals(key))return item.visible(values);
        return true; // Dynamic system-icon keys are validated by SettingsCoordinator.
    }
    private static boolean canEditNumber(String group,SettingsCatalog.Item item,Map<String,Object> values){
        return SettingsCatalog.controlEnabled(group,item.key(),values) && item.visible(values) && item.valueEnabled(values);
    }
    private static String unavailable(SettingsCoordinator.State state){
        return state.revision()<0?"unavailable":state.writable()?null:"uncertain";
    }
    private static String preflight(SettingsCoordinator.State state,long revision){
        String unavailable=unavailable(state);
        return unavailable!=null?unavailable:state.revision()==revision?null:"conflict";
    }
}
