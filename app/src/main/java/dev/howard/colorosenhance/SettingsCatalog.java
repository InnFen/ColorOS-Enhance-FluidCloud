package dev.howard.colorosenhance;

import dev.lsposed.fluidcloud.MediaWaveBands;
import dev.lsposed.fluidcloud.MediaWaveOptions;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.StringJoiner;

/** Values use integer units so tenths of a dp persist without float drift. */
public final class SettingsCatalog {
    public static final String RESET_NUMBER_LABEL = "恢复此项默认值";
    public static final String WAVE_BANDS_TITLE = "六频段设置";
    public static final String WAVE_BANDS_HELP = "拖动频率轴上的分界点，或在下方输入 20–24000 Hz。六个区间从左到右对应六根柱。没有可用频点的区间显示为短柱。";
    public static final String WAVE_BANDS_ERROR = "请输入 20–24000 Hz 内严格递增的整数。";
    public static final String WAVE_RESET_TITLE = "恢复实时律动默认配置";
    public static final String WAVE_RESET_HELP = "恢复六频段、读取间隔、灵敏度、平滑时间和暂停时的柱高选项。";

    public static String waveBandEdgeTitle(int index) {
        if (index == 0) return "起始频率（Hz）";
        if (index == MediaWaveBands.EDGE_COUNT - 1) return "结束频率（Hz）";
        return "第 " + index + "／" + (index + 1) + " 段分界（Hz）";
    }

    public static String waveBandsSummary(MediaWaveBands bands) {
        StringJoiner ranges = new StringJoiner(" · ");
        for (int index = 0; index < MediaWaveBands.EDGE_COUNT - 1; index++) {
            ranges.add(bands.edgeHz(index) + "–" + bands.edgeHz(index + 1));
        }
        return ranges + " Hz";
    }

    public static final String INSTALLER_HELP = "选择已安装且能处理 APK 的应用，接管普通应用打开 APK 的请求。请先在所选应用中完成安装方式与权限配置。\n\n"
            + "首次使用或更新此功能后，确认 LSPosed 作用域包含“系统框架”，并完整重启手机。加载后开关对后续请求生效；“一键重启作用域”不能加载此功能。\n\n"
            + "未选择应用、目标被卸载或停用、无法处理当前 APK 请求时，保留系统流程。ADB、卸载、安装会话确认和跨用户请求保持原流程。";
    public record Visibility(String key, boolean value) { }
    public record SceneSummary(String keepKey, String animationKey) { }
    public record Item(String section, String key, String title, String description, boolean defaultFlag,
                       String valueKey, int valueDefault, int min, int max, int step, int divisor, String unit,
                       Visibility visibility, SceneSummary sceneSummary) {
        public boolean adjustable() { return valueKey != null; }
        public boolean valueOnly() { return adjustable() && key.equals(valueKey); }
        public boolean valueEnabled(Map<String,?> values) {
            return valueOnly() || (values.containsKey(key)?Boolean.TRUE.equals(values.get(key)):defaultFlag);
        }
        public boolean visible(Map<String, ?> values) {
            if (key.startsWith("landscape_") && !key.equals("landscape_follow_portrait")
                    && Boolean.TRUE.equals(values.get("landscape_follow_portrait"))) return false;
            if (visibility == null) return true;
            return Boolean.TRUE.equals(values.get(visibility.key())) == visibility.value();
        }
        public String summary(Map<String, ?> values) {
            if (sceneSummary == null) return description;
            String mode = Boolean.TRUE.equals(values.get(sceneSummary.keepKey())) ? "场景期间保持显示"
                    : Boolean.TRUE.equals(values.get(sceneSummary.animationKey())) ? "使用收缩/展开动画"
                    : "跟随系统";
            return description == null || description.isBlank() ? mode : description + " · " + mode;
        }
    }
    public record Group(String key, String title, String summary, List<Item> items) { }
    public static String masterTitle(Group group) {
        return "启用" + group.title() + (List.of("aod", "cloud", "battery", "icons", "control_center").contains(group.key()) ? "增强" : "");
    }
    private static Item flag(String section,String key,String title,String description) {
        return flag(section,key,title,description,false);
    }
    private static Item flag(String section,String key,String title,String description,boolean fallback) {
        return new Item(section,key,title,description,fallback,null,0,0,0,1,1,"",null,null);
    }
    private static Item value(String section,String key,String title,String description,String valueKey,
                              int valueDefault,int min,int max,int step,int divisor,String unit) {
        return new Item(section,key,title,description,false,valueKey,valueDefault,min,max,step,divisor,unit,null,null);
    }
    private static List<Item> cloudItems() {
        List<Item> items = new ArrayList<>();
        items.add(flag("媒体","keep_paused_music","保持媒体流体云",
                "有效媒体会话未播放或暂停时也保持显示；会话结束或应用退出后恢复系统处理。"));
        items.add(flag("媒体","realtime_media_wave","媒体实时律动",
                "六柱从左到右对应低频至高频，可在下方自定义分界。仅播放且小胶囊可见时采集，硬件卸载或无法安全采集时使用原厂动画。更新后需重启系统界面。"));
        MediaWaveOptions defaults = MediaWaveOptions.DEFAULTS;
        items.add(waveNumber("wave_interval_ms","读取间隔","越短响应越快，计算次数也越多。建议先使用 80 ms。",
                defaults.intervalMs(), MediaWaveOptions.INTERVAL_MIN_MS, MediaWaveOptions.INTERVAL_MAX_MS, MediaWaveOptions.INTERVAL_STEP_MS, "ms"));
        items.add(waveNumber("wave_sensitivity","灵敏度","调整六个频段的整体柱高。",
                defaults.sensitivityPercent(), MediaWaveOptions.SENSITIVITY_MIN_PERCENT, MediaWaveOptions.SENSITIVITY_MAX_PERCENT, MediaWaveOptions.SENSITIVITY_STEP_PERCENT, "%"));
        items.add(waveNumber("wave_attack_ms","上升平滑时间","越小越快跟随声音增强，越大越平缓。",
                defaults.attackMs(), MediaWaveOptions.ATTACK_MIN_MS, MediaWaveOptions.ATTACK_MAX_MS, MediaWaveOptions.ATTACK_STEP_MS, "ms"));
        items.add(waveNumber("wave_release_ms","回落平滑时间","越小回落越快，越大保留起伏越久。",
                defaults.releaseMs(), MediaWaveOptions.RELEASE_MIN_MS, MediaWaveOptions.RELEASE_MAX_MS, MediaWaveOptions.RELEASE_STEP_MS, "ms"));
        items.add(new Item("媒体实时律动参数","wave_hold_paused","暂停时保留最后柱高",
                "开启后冻结暂停那一刻的六柱；关闭时平滑回落为短柱。两种模式均释放声音采集器。",false,
                null,0,0,0,1,1,"",new Visibility("realtime_media_wave",true),null));
        items.add(value("小胶囊外观","capsule_size_enabled","自定义小胶囊尺寸",
                "下方调节横向长度，100% 为原厂尺寸。不拉伸文字和图标；尺寸过小时内容可能裁剪，最大高度受状态栏空间限制。",
                "capsule_width_percent",100,50,200,1,1,"%"));
        items.add(new Item("小胶囊外观","capsule_height_percent","纵向高度",
                "按原厂高度的比例调整，100% 保持原厂。",false,"capsule_height_percent",100,50,200,1,1,"%",
                new Visibility("capsule_size_enabled",true),null));
        items.add(flag("小胶囊外观","hide_capsule_stroke","隐藏小胶囊边框",
                "隐藏深色背景下的原生边框；关闭后按当前背景恢复系统处理。"));
        items.add(flag("小胶囊外观","hide_capsule_arrows","隐藏左右切换提示",
                "隐藏小胶囊两侧的切换指示，不影响左右滑动。关闭后恢复原生提示规则。"));
        items.add(value("小胶囊外观","capsule_corners_enabled","自定义小胶囊圆角",
                "调节半径，最大值随胶囊高度限制。关闭后恢复原厂外形。","capsule_corner_dp",16,0,24,1,1,"dp"));
        items.add(flag("小胶囊外观","capsule_continuous_corners","小胶囊连续圆角",
                "启用自定义圆角后，使直边与弯曲边缘平滑衔接。"));
        items.add(flag("小胶囊切换","skip_empty_capsule","跳过空白状态，循环切换",
                "左右滑动仅在当前有效流体云之间循环；只有一个时保持当前项。不影响系统主动隐藏流体云。"));
        items.add(flag("点击交互","block_capsule_click_top","防止点击流体云触发回顶",
                "点击流体云时只执行原生流体云操作，不同时让当前应用回到顶部；状态栏其他区域保持系统处理。"));
        items.add(flag("展开卡片","selected_card_first","当前胶囊卡片优先置顶",
                "展开时将当前小胶囊对应卡片放在最上面；左右滑动切换胶囊后同步重排，其余卡片保留相对顺序。关闭后恢复原厂排序。"));
        items.add(flag("展开卡片","camera_card_expansion","从摄像头区域展开",
                "卡片顶部贴合摄像头区域，展开与收起使用同一位置，并为内容保留避让空间。横屏挖孔位于侧边时保持系统处理。更新模块后需重启系统界面。"));
        items.add(new Item("展开卡片",dev.lsposed.fluidcloud.CameraAvoidance.KEY,"摄像头避让空间",
                "卡片内容上方为摄像头留出的空白。100% 为原版；0% 时内容紧贴摄像头下缘，不会盖住摄像头。重新展开卡片后生效。",false,
                dev.lsposed.fluidcloud.CameraAvoidance.KEY,dev.lsposed.fluidcloud.CameraAvoidance.DEFAULT_PERCENT,0,100,5,1,"%",
                new Visibility("camera_card_expansion",true),null));
        items.add(value("展开卡片","card_dim_enabled","展开时压暗背景",
                "随卡片展开加深、收起减弱；在通知中心或控制中心中也生效。关闭恢复原厂。",
                "card_dim_percent",30,0,100,1,1,"%"));
        items.add(value("展开卡片","card_corners_enabled","自定义展开卡片圆角",
                "调节展开卡片的半径，重新展开卡片后生效。","card_corner_dp",28,0,64,1,1,"dp"));
        items.add(flag("展开卡片","card_continuous_corners","展开卡片连续圆角",
                "启用自定义圆角后，使直边与弯曲边缘平滑衔接。重新展开卡片后生效。"));
        items.add(flag("场景控制","unbind_status_bar","流体云与状态栏解绑",
                "开启后重启系统界面，加载下方场景；横竖屏独立配置，横屏默认跟随系统。关闭后立即恢复系统处理。"));
        items.add(flag("场景控制","capsule_on_top","流体云胶囊置顶",
                "提高胶囊窗口层级，胶囊可显示在展开卡片上方。修改后重启系统界面。"));
        items.add(flag("场景控制","capsule_keep_during_launch","应用转场时保持胶囊显示",
                "跳过同方向应用转场中的胶囊淡出，例如从控制中心打开设置。首次使用需重启手机。之后修改在下次转场生效，显隐仍遵循场景设置。",true));
        addOrientationScenes(items, "竖屏", "");
        items.add(flag("横屏配置", "landscape_follow_portrait", "横屏与竖屏一致",
                "开启后横屏沿用竖屏的全部场景设置；关闭后单独配置横屏，已保存的横屏选项会保留。"));
        addOrientationScenes(items, "横屏", "landscape_");
        return List.copyOf(items);
    }
    private static Item waveNumber(String key,String title,String description,int fallback,int min,int max,int step,String unit){
        return new Item("媒体实时律动参数",key,title,description,false,key,fallback,min,max,step,1,unit,
                new Visibility("realtime_media_wave",true),null);
    }
    private static void addOrientationScenes(List<Item> items, String orientation, String prefix) {
        addScene(items,orientation+" · 进入/退出全屏",prefix+"fullscreen",prefix+"fullscreen_animation",
                "应用隐藏状态栏时按所选模式处理流体云。",false);
        addScene(items,orientation+" · 进入/退出最近任务",prefix+"recents",prefix+"recents_shrink",
                "使用已适配的最近任务状态栏请求。",false);
        items.add(flag(orientation+" · 下拉状态栏",prefix+"shade_keep","保持显示",
                "下拉通知栏或控制中心时保持普通流体云；锁屏、隐私状态和迷你胶囊仍由系统处理。"));
        items.add(new Item(orientation+" · 下拉状态栏",prefix+"shade_collapse_on_open","展开流体云前收起面板",
                "开启时先收起通知中心或控制中心，再展开流体云；关闭时直接在面板上展开。",false,
                null,0,0,0,1,1,"",new Visibility(prefix+"shade_keep",true),null));
        addScene(items,orientation+" · 其他隐藏场景",prefix+"other_keep",prefix+"other_animation",
                "实验性：仅处理通过安全检查且能够配对恢复的未知隐藏请求。",true);
    }
    public static boolean controlEnabled(String group,String key,Map<String,?> values) {
        if ("icons".equals(group)) return Boolean.TRUE.equals(values.get("enabled"));
        if (!"cloud".equals(group)) return true;
        if (!Boolean.TRUE.equals(values.get("enabled"))) return false;
        if(key.startsWith("wave_"))return Boolean.TRUE.equals(values.get("realtime_media_wave"));
        if ("capsule_height_percent".equals(key)) return Boolean.TRUE.equals(values.get("capsule_size_enabled"));
        if (dev.lsposed.fluidcloud.CameraAvoidance.KEY.equals(key)) return Boolean.TRUE.equals(values.get("camera_card_expansion"));
        if ("capsule_continuous_corners".equals(key)) return Boolean.TRUE.equals(values.get("capsule_corners_enabled"));
        if ("card_continuous_corners".equals(key)) return Boolean.TRUE.equals(values.get("card_corners_enabled"));
        if (key.startsWith("landscape_") && !key.equals("landscape_follow_portrait")
                && Boolean.TRUE.equals(values.get("landscape_follow_portrait"))) return false;
        if ("shade_collapse_on_open".equals(key) && !Boolean.TRUE.equals(values.get("shade_keep"))) return false;
        if ("landscape_shade_collapse_on_open".equals(key) && !Boolean.TRUE.equals(values.get("landscape_shade_keep"))) return false;
        return "keep_paused_music".equals(key) || "realtime_media_wave".equals(key) || "block_capsule_click_top".equals(key)
                || "camera_card_expansion".equals(key) || "unbind_status_bar".equals(key)
                || List.of("hide_capsule_stroke","hide_capsule_arrows","skip_empty_capsule","card_dim_enabled",
                        "capsule_corners_enabled","card_corners_enabled","capsule_size_enabled","selected_card_first").contains(key)
                || Boolean.TRUE.equals(values.get("unbind_status_bar"));
    }
    public static boolean sectionVisible(Group group, String section, Map<String, ?> values) {
        return group.items().stream().anyMatch(item -> item.section().equals(section) && item.visible(values));
    }
    private static void addScene(List<Item> items, String section, String keepKey, String animationKey,
                                 String description, boolean experimental) {
        SceneSummary summary = new SceneSummary(keepKey,animationKey);
        items.add(new Item(section,keepKey,"保持显示",description,false,
                null,0,0,0,1,1,"",null,summary));
        items.add(new Item(section,animationKey,"动画补全",
                description,false,null,0,0,0,1,1,"",
                new Visibility(keepKey,false),summary));
    }
    // Fluid-cloud-only build: battery, status icons, control center, AOD, SIM PIN and installer groups removed.
    public static final List<Group> GROUPS = List.of(
        new Group("cloud", "流体云", "按场景选择保持显示、动画补全或跟随系统", cloudItems())
    );
    public static Group find(String key) {
        return GROUPS.stream().filter(group -> group.key().equals(key)).findFirst().orElse(null);
    }
    public static Group find(String key, android.content.Context context) {
        Group group = find(key);
        return group != null && "icons".equals(key) ? new Group(group.key(), group.title(), group.summary(),
                dev.howard.colorosenhance.icons.StatusIconCatalog.items(context)) : group;
    }
}
