package dev.howard.colorosenhance;

import android.content.Context;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Searches presentation metadata only; never reads configured values, credentials or controller state. */
public final class SettingsSearch {
    public record Result(String route,String title,String summary) { }
    private record ItemKey(String group,String key) { }
    private static final List<Result> ACTIONS=List.of(
            new Result("cloud_media", SettingsCatalog.WAVE_BANDS_TITLE, "媒体实时律动 · 自定义六频段分界和恢复默认频率"),
            new Result("cloud_media", SettingsCatalog.WAVE_RESET_TITLE, "媒体实时律动 · 一次恢复全部调节参数")
    );
    private SettingsSearch() { }

    /** Groups precede subpages, actions and individual options, with deterministic ordering in each tier. */
    public static List<Result> search(Context context,String query){
        String needle=normalize(query);
        if(needle.isEmpty())return List.of();
        Objects.requireNonNull(context,"context");
        var results=new ArrayList<Result>();
        var pages=orderedPages();
        // Keep feature groups ahead of their options: PIN must first offer the SIM and slot pages.
        for(var page:pages.values())addMatch(results,new Result(page.route(),page.title(),page.summary()),needle);
        for(var action:ACTIONS)addMatch(results,action,needle);

        var seen=new LinkedHashSet<ItemKey>();
        for(var page:pages.values()){
            if(page.route().equals("home"))continue;
            for(var item:SettingsLayout.items(context,page.route())){
                if(!seen.add(new ItemKey(page.group(),item.key())))continue;
                var group=SettingsCatalog.find(page.group());
                var location=new LinkedHashSet<String>();
                location.add(group.title());
                if(!page.route().equals(page.group()))location.add(page.title());
                location.add(item.section());
                String summary=String.join(" · ",location)+"\n"+Objects.toString(item.description(),"");
                addMatch(results,new Result(page.route(),item.title(),summary),needle);
            }
        }
        return List.copyOf(results);
    }

    private static LinkedHashMap<String,SettingsLayout.Page> orderedPages(){
        var pages=new LinkedHashMap<String,SettingsLayout.Page>();
        // Start with all groups in the same stable order as the feature library.
        for(String key:SettingsLayout.DISPLAY_GROUPS)putPage(pages,key);
        for(String key:SettingsLayout.SYSTEM_GROUPS)putPage(pages,key);
        for(var group:SettingsCatalog.GROUPS)putPage(pages,group.key());
        for(var group:SettingsCatalog.GROUPS)putChildren(pages,group.key());
        // The route registry returns a Set: sort any later additions rather than relying on its iteration order.
        SettingsLayout.routes().stream().sorted().forEach(route->putPage(pages,route));
        return pages;
    }
    private static void putPage(LinkedHashMap<String,SettingsLayout.Page> pages,String route){
        var page=SettingsLayout.page(route);if(page!=null)pages.putIfAbsent(route,page);
    }
    private static void putChildren(LinkedHashMap<String,SettingsLayout.Page> pages,String route){
        for(var child:SettingsLayout.children(route)){
            if(pages.putIfAbsent(child.route(),child)==null)putChildren(pages,child.route());
        }
    }
    private static void addMatch(List<Result> results,Result candidate,String needle){
        if(normalize(candidate.title()+" "+candidate.summary()).contains(needle))results.add(candidate);
    }
    private static String normalize(String value){
        if(value==null)return "";
        return Normalizer.normalize(value,Normalizer.Form.NFKC).replaceAll("[\\s\\p{Z}]+","").toLowerCase(Locale.ROOT);
    }
}
