package dev.howard.colorosenhance.icons;

import java.util.*;

/** Both physical sides are one revisioned value; a slot can never be saved on both sides. */
public record IconOrder(List<String> left, List<String> right) {
    public static final String KEY="order_layout";
    public static final IconOrder EMPTY=new IconOrder(List.of(),List.of());
    public IconOrder {
        left=List.copyOf(left);right=List.copyOf(right);
        Set<String> seen=new HashSet<>();
        if(left.size()+right.size()>128)throw new IllegalArgumentException("Too many slots");
        for(String slot:concat(left,right))if(!StatusIconConfig.validSlot(slot)||!seen.add(slot))
            throw new IllegalArgumentException("Invalid or duplicate slot");
    }
    public String encode(){return "v1|"+String.join(",",left)+"|"+String.join(",",right);}
    public static boolean valid(String value){try{decode(value);return true;}catch(IllegalArgumentException invalid){return false;}}
    public static IconOrder decode(String value){
        if(value==null || value.length()>13000)throw new IllegalArgumentException("Invalid layout");
        if(value.isEmpty())return EMPTY;
        String[] parts=value.split("\\|",-1);
        if(parts.length!=3 || !parts[0].equals("v1"))throw new IllegalArgumentException("Unknown layout");
        return new IconOrder(parts[1].isEmpty()?List.of():Arrays.asList(parts[1].split(",",-1)),
                parts[2].isEmpty()?List.of():Arrays.asList(parts[2].split(",",-1)));
    }
    /** New/unsaved slots retain their native relative order. Saved unavailable slots remain saved. */
    public IconOrder withAvailable(Collection<String> slots){
        List<String> l=new ArrayList<>(left),r=new ArrayList<>(right);Set<String> known=new HashSet<>(concat(l,r));
        for(String slot:slots)if(StatusIconConfig.validSlot(slot)&&known.add(slot)){
            if(slot.equals("clock"))l.add(0,slot);
            else if(slot.equals("battery"))r.add(slot);
            else {int battery=r.indexOf("battery");r.add(battery<0?r.size():battery,slot);}
        }
        return new IconOrder(l,r);
    }
    /** Legacy “静音” actually represented the ColorOS ringer volume slot. First placement wins. */
    IconOrder ringerSlot(){
        List<String> l=new ArrayList<>(),r=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(String slot:left){String nativeSlot=slot.equals("mute")?"volume":slot;if(seen.add(nativeSlot))l.add(nativeSlot);}
        for(String slot:right){String nativeSlot=slot.equals("mute")?"volume":slot;if(seen.add(nativeSlot))r.add(nativeSlot);}
        return new IconOrder(l,r);
    }
    public IconOrder move(String slot,boolean toLeft,int index){
        List<String> l=new ArrayList<>(left),r=new ArrayList<>(right);
        if(!l.remove(slot)&&!r.contains(slot))throw new IllegalArgumentException("Unknown slot");
        r.remove(slot);List<String> target=toLeft?l:r;
        target.add(Math.max(0,Math.min(index,target.size())),slot);return new IconOrder(l,r);
    }
    public boolean onLeft(String slot){return left.contains(slot);}
    private static List<String> concat(List<String> a,List<String> b){var all=new ArrayList<>(a);all.addAll(b);return all;}
}
