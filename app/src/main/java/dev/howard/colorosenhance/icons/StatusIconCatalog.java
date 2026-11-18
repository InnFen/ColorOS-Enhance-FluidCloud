package dev.howard.colorosenhance.icons;

import android.content.Context;
import android.content.res.Resources;
import dev.howard.colorosenhance.SettingsCatalog;
import java.util.*;

/** Uses the firmware's slot inventory, including vendor slots not known at build time. */
public final class StatusIconCatalog {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("clock", "时钟"), Map.entry("battery", "电池"), Map.entry("bluetooth", "蓝牙"),
            Map.entry("wifi", "Wi-Fi"), Map.entry("mobile", "移动网络信号"), Map.entry("airplane", "飞行模式"),
            Map.entry("alarm_clock", "闹钟"), Map.entry("volume", "静音／振动"), Map.entry("mute", "静音（独立槽位）"),
            Map.entry("zen", "勿扰模式"), Map.entry("rotate", "旋转锁定"), Map.entry("headset", "有线耳机"),
            Map.entry("vpn", "VPN"), Map.entry("location", "定位"), Map.entry("hotspot", "个人热点"),
            Map.entry("ethernet", "以太网"), Map.entry("nfc", "NFC"), Map.entry("cast", "投屏"),
            Map.entry("network_speed", "实时网速"), Map.entry("networkspeed", "实时网速"),
            Map.entry("data_saver", "流量节省"), Map.entry("managed_profile", "工作资料"),
            Map.entry("tty", "TTY"), Map.entry("speakerphone", "扬声器"), Map.entry("ime", "输入法"),
            Map.entry("sync_active", "正在同步"), Map.entry("sync_failing", "同步失败"),
            Map.entry("vowifi", "Wi-Fi 通话"), Map.entry("volte", "VoLTE"), Map.entry("hd", "高清通话"),
            Map.entry("high_performance", "高性能模式"), Map.entry("super_power_save", "超级省电"),
            Map.entry("sensors_off", "传感器关闭"), Map.entry("connected_display", "外接显示器"),
            Map.entry("logging", "日志记录"), Map.entry("sos", "紧急呼救"),
            Map.entry("microphone", "麦克风状态图标"), Map.entry("camera", "摄像头状态图标"),
            Map.entry("cdma_eri", "CDMA 漫游"), Map.entry("data_connection", "数据连接"),
            Map.entry("phone_evdo_signal", "EVDO 信号（旧样式）"), Map.entry("phone_signal", "蜂窝信号（旧样式）"),
            Map.entry("secure", "安全连接"), Map.entry("satellite", "卫星通信"), Map.entry("oplus_satellite", "卫星连接"),
            Map.entry("scenario_assistant", "场景助手"), Map.entry("stacked_mobile", "叠加式移动信号"),
            Map.entry("nosim_all", "未插入 SIM 卡"), Map.entry("nosim_one", "SIM 1 未插入"), Map.entry("nosim_two", "SIM 2 未插入"),
            Map.entry("dynamic_info", "动态信息"), Map.entry("secure_pay", "支付保护"),
            Map.entry("privacy_call", "通话隐私保护"), Map.entry("stealth_mode", "隐身模式"));
    private static final List<String> COMMON = List.of("clock", "battery", "bluetooth", "wifi", "mobile", "stacked_mobile",
            "network_speed", "networkspeed", "alarm_clock", "volume", "mute", "zen", "vpn", "location", "hotspot", "airplane", "nfc");
    private StatusIconCatalog() {}
    public static String label(String slot){return LABELS.getOrDefault(slot,"系统图标（"+slot+"）");}
    public static List<SettingsCatalog.Item> items(Context context){return items(slots(context));}
    @android.annotation.SuppressLint("DiscouragedApi") // Slot names are firmware resources, not application IDs.
    public static List<String> slots(Context context) {
        LinkedHashSet<String> slots = new LinkedHashSet<>();
        try {
            Resources resources = context.createPackageContext("com.android.systemui", 0).getResources();
            int array = resources.getIdentifier("config_statusBarIcons", "array", "com.android.systemui");
            if (array != 0) slots.addAll(Arrays.asList(resources.getStringArray(array)));
        } catch (android.content.pm.PackageManager.NameNotFoundException | Resources.NotFoundException | SecurityException ignored) {
            /* Fall back to platform slots if the host doesn't expose SystemUI resources. */
        }
        if (slots.isEmpty()) {
            try {
                int array = context.getResources().getIdentifier("config_statusBarIcons", "array", "android");
                if (array != 0) slots.addAll(Arrays.asList(context.getResources().getStringArray(array)));
            } catch (Resources.NotFoundException ignored) { /* Baseline below. */ }
        }
        if (slots.isEmpty()) slots.addAll(List.of("bluetooth", "wifi", "mobile", "airplane", "alarm_clock", "volume", "zen", "vpn", "location", "network_speed"));
        if(usesColorOsRingerSlot(slots))slots.remove("mute");
        slots.remove("clock");slots.remove("battery");slots.add("battery");
        List<String> result=new ArrayList<>();result.add("clock");
        for(String slot:slots)if(StatusIconConfig.validSlot(slot))result.add(slot);
        return List.copyOf(result);
    }
    static List<SettingsCatalog.Item> items(Collection<String> slots) {
        if(usesColorOsRingerSlot(slots)){var canonical=new ArrayList<>(slots);canonical.removeIf("mute"::equals);slots=canonical;}
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String slot : COMMON) if (slots.contains(slot)) unique.add(slot);
        unique.addAll(slots);
        List<SettingsCatalog.Item> result = new ArrayList<>();
        result.add(new SettingsCatalog.Item("图标排序","order_enabled","自定义状态栏图标排序",
                "编辑左右区域，支持拖动跨区移动，包括时钟与电池。仅调整普通状态栏；下拉面板保留系统布局与过渡。图标过多时按可用空间显示。关闭后恢复原厂顺序并保留配置。",
                false,null,0,0,0,1,1,"",null,null));
        result.add(new SettingsCatalog.Item("锁屏", "keyguard_follow", "锁屏沿用图标隐藏规则",
                "锁屏状态栏也遵循系统隐藏设置、蓝牙显示条件和“仅下拉后显示”选择。仅下拉图标在展开面板时显示；不影响锁屏大时钟和息屏。关闭后恢复锁屏原生处理。",
                true, null, 0, 0, 0, 1, 1, "", null, null));
        result.add(new SettingsCatalog.Item("高清通话", "force_hd_home", "普通状态栏显示高清通话",
                "解除系统默认仅在下拉面板显示 VoLTE 的限制；仍需当前网络支持，且系统设置未关闭该图标。若同时开启 VoLTE 的“仅下拉后显示”，则以下方设置为准。",
                false, null, 0, 0, 0, 1, 1, "", null, null));
        for (String slot : unique) if (StatusIconConfig.validSlot(slot)) {
            String title = LABELS.getOrDefault(slot, "系统图标（" + slot + "）");
            result.add(new SettingsCatalog.Item("仅下拉后显示", StatusIconConfig.PREFIX + slot, title,
                    "普通状态栏隐藏，下拉通知中心或控制中心后按系统状态显示。" +
                            ("stacked_mobile".equals(slot) ? " 双卡叠加样式使用本项。" : "") +
                            (LABELS.containsKey(slot) ? "" : " 图标标识：" + slot),
                    false, null, 0, 0, 0, 1, 1, "", null, null));
        }
        return List.copyOf(result);
    }
    /** Known ColorOS 16/17 inventory: the ringer policy writes silent/vibrate to volume. */
    public static boolean usesColorOsRingerSlot(Collection<String> slots){
        return usesColorOsRingerSlot(slots,android.os.Build.VERSION.SDK_INT);
    }
    static boolean usesColorOsRingerSlot(Collection<String> slots,int sdk){return (sdk==36 || sdk==37) && slots.contains("volume");}
}
