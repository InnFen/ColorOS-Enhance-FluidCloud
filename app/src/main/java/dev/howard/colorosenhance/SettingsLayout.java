package dev.howard.colorosenhance;

import android.content.Context;
import java.util.List;
import java.util.Set;

/** Presentation routes only. Existing preference namespaces and keys are never renamed. */
public final class SettingsLayout {
    public record Page(String route,String group,String title,String summary,String demo,String parent) { }
    private static final List<Page> PAGES=List.of(
            new Page("cloud_media","cloud","媒体与实时律动","媒体常驻、六频段与采样参数","media","cloud"),
            new Page("cloud_capsule","cloud","小胶囊与交互","尺寸、圆角、边框、循环切换与点击","capsule","cloud"),
            new Page("cloud_cards","cloud","展开卡片","圆角、摄像头区域展开与背景压暗","camera","cloud"),
            new Page("cloud_scenes","cloud","场景与方向","状态栏解绑，按场景选择显示方式","scenes","cloud"),
            new Page("cloud_portrait","cloud","竖屏场景","全屏、最近任务、下拉与其他场景","scenes","cloud_scenes"),
            new Page("cloud_landscape","cloud","横屏场景","独立配置，或沿用竖屏选择","scenes","cloud_scenes")
    );
    // Fluid-cloud-only build: every other feature group is removed.
    public static final List<String> DISPLAY_GROUPS=List.of("cloud");
    public static final List<String> HOME_GROUPS=List.of("cloud");
    public static final List<String> SYSTEM_GROUPS=List.of();
    private SettingsLayout() { }
    public static Page page(String route) {
        for(Page page:PAGES)if(page.route().equals(route))return page;
        SettingsCatalog.Group group=SettingsCatalog.find(route);
        return group==null?null:new Page(route,route,group.title(),group.summary(),switch(route){
            case "battery"->"battery";case "aod"->"aod";case "icons"->"icons";case "cloud"->"camera";default->"";
        },"home");
    }
    public static boolean known(String route){return "home".equals(route)||page(route)!=null;}
    public static String group(String route){Page page=page(route);return page==null?route:page.group();}
    public static List<Page> children(String route){return PAGES.stream().filter(page->page.parent().equals(route)).toList();}
    public static List<SettingsCatalog.Item> items(Context context,String route){
        Page page=page(route);if(page==null)return List.of();
        SettingsCatalog.Group group=SettingsCatalog.find(page.group(),context);
        if(group==null)return List.of();
        return group.items().stream().filter(item->belongs(route,item)).toList();
    }
    private static boolean belongs(String route,SettingsCatalog.Item item){
        String section=item.section();
        return switch(route){
            case "cloud" -> false;
            case "cloud_media" -> section.equals("媒体")||section.equals("媒体实时律动参数");
            case "cloud_capsule" -> section.startsWith("小胶囊")||section.equals("点击交互");
            case "cloud_cards" -> section.equals("展开卡片");
            case "cloud_scenes" -> section.equals("场景控制")||item.key().equals("landscape_follow_portrait");
            case "cloud_portrait" -> section.startsWith("竖屏");
            case "cloud_landscape" -> section.startsWith("横屏")&&!item.key().equals("landscape_follow_portrait");
            case "icons" -> !section.equals("仅下拉后显示");
            case "icons_visibility" -> section.equals("仅下拉后显示");
            case "sim_slots" -> false;
            default -> true;
        };
    }
    public static Set<String> routes(){
        var routes=new java.util.LinkedHashSet<String>();routes.add("home");
        for(var group:SettingsCatalog.GROUPS)routes.add(group.key());
        for(var page:PAGES)routes.add(page.route());return Set.copyOf(routes);
    }
}
