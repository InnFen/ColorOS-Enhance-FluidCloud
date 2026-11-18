package dev.howard.colorosenhance.settings;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import dev.howard.colorosenhance.SettingsCatalog;
import dev.howard.colorosenhance.SettingsLayout;
import dev.howard.colorosenhance.EnhanceApplication;
import dev.howard.colorosenhance.bridge.SettingsBridgeClient;
import dev.lsposed.fluidcloud.MediaWaveBands;
import java.util.*;
import java.util.function.Consumer;
final class NativeSettingsPage {
    interface Backend { void connect(); void close(); boolean isConnected(); void request(Bundle request, Consumer<Bundle> reply); }
    interface Factory { Backend create(Context context, SettingsBridgeClient.Listener listener); }
    private static final String CATEGORY="com.oplus.settings.widget.preference.SettingsPreferenceCategory";
    private static final String JUMP="com.oplus.settings.widget.preference.SettingsSimpleJumpPreference";
    private static final String SWITCH="com.coui.appcompat.preference.COUISwitchPreference";
    private static final String NUMBER="com.coui.appcompat.preference.COUIPreference";
    private final Object fragment;
    private final HostReflection host;
    private final String route;
    private final String pageRoute;
    private final Context context;
    private final Factory factory;
    private final NativeDialogs dialogs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Object> controls=new LinkedHashMap<>();
    private final Map<Object,NativeNumberRow> numberRows=new IdentityHashMap<>();
    private NativePageInsets pageInsets;
    private boolean insetsInitialized;
    private final Map<String,Bundle> states=new HashMap<>();
    private final Set<String> freshGroups=new HashSet<>();
    private final Map<String,Integer> numberPreviews=new HashMap<>();
    private final Map<String,String> readNotices=new LinkedHashMap<>();
    private final Set<String> busy=new HashSet<>(),reading=new HashSet<>(),readAgain=new HashSet<>();
    private final Set<String> visibleBusy=new HashSet<>();
    private final Map<String,Long> busyTickets=new HashMap<>();
    private long nextBusyTicket;
    private Object screen,statusRow;
    private Backend backend;
    private Bundle simState;
    private Bundle managementState;
    private boolean active,simLoaded,refreshQueued;
    private int generation;
    private String notice="正在连接…";

    NativeSettingsPage(Object fragment,HostReflection host,String route) throws ReflectiveOperationException {
        this(fragment,host,route,(context,listener)-> {
            SettingsBridgeClient client=new SettingsBridgeClient(context,listener);
            return new Backend() {
                public void connect() { client.connect(); }
                public void close() { client.close(); }
                public boolean isConnected() { return client.isConnected(); }
                public void request(Bundle request,Consumer<Bundle> reply) { client.request(request,reply); }
            };
        });
    }
    NativeSettingsPage(Object fragment,HostReflection host,String route,Factory factory) throws ReflectiveOperationException {
        if(!SettingsLayout.known(route))throw new IllegalArgumentException("Unknown page");
        this.fragment=fragment; this.host=host; this.pageRoute=route;
        this.route="sim_slots".equals(route)?route:SettingsLayout.group(route); this.factory=factory;
        context=(Context)host.call(fragment,"getContext");
        dialogs=new NativeDialogs(context,host,this::message);
    }
    void create() throws ReflectiveOperationException {
        screen=host.call(host.call(fragment,"getPreferenceManager"),"createPreferenceScreen",context);
        host.call(screen,"setPersistent",false);
        Object information=category("information",null);
        statusRow=jump(information,"status","连接状态",()->retry());
        if("home".equals(route)) {
            for(var section:List.of(SettingsLayout.DISPLAY_GROUPS,SettingsLayout.SYSTEM_GROUPS)){
                Object groups=category(section==SettingsLayout.DISPLAY_GROUPS?"display":"system",
                        section==SettingsLayout.DISPLAY_GROUPS?"界面与显示":"系统与安全");
                for(String key:section){var group=SettingsCatalog.find(key);
                    jump(groups,"open_"+group.key(),group.title(),()->open(group.key()));}
            }
            Object integration=category("integration","模块管理");
            toggle(integration,"launcher_visible","显示桌面图标","隐藏后可从此页面或 LSPosed 模块设置恢复，桌面刷新可能稍有延迟。",value->{
                Bundle request=command("launcher_visibility"); request.putBoolean("visible",value); managementAction(request);
            });
            toggle(integration,"integration.show_entry","在系统设置中显示入口","关闭后可在独立应用中重新开启。",
                    value->put("integration","show_entry",value));
            jump(integration,"restart_scopes","一键重启作用域",()->dialogs.confirm("重启三个作用域",EnhanceApplication.RESTART_CONFIRMATION,()->{
                Bundle request=command("restart_scopes"); request.putBoolean("confirmed",true); managementAction(request);
            }));
            Object about=category("about","关于");
            Object updates=jump(about,"check_updates","检查更新",()->dev.howard.colorosenhance.updates.UpdateLinks.check(context));
            host.call(updates,"setSummary","当前版本 "+dev.howard.colorosenhance.BuildConfig.VERSION_NAME+" · 在模块应用中手动检查正式版");
            Object repository=jump(about,"github_repository","GitHub 仓库",()->dev.howard.colorosenhance.updates.UpdateLinks.repository(context));
            host.call(repository,"setSummary","源码、使用说明与问题反馈");
        } else if("sim_slots".equals(route)) {
            for(int slot=0;slot<2;slot++) {
                final int id=slot;
                Object category=category("slot"+slot,"卡槽 "+(slot+1));
                Object state=jump(category,"slot"+slot+".status","卡槽状态",()->refresh(true));
                host.call(state,"setSummary","正在读取…");
                toggle(category,"slot"+slot+".enabled","自动解锁（已确认状态）",null,value->{
                    Bundle request=command("sim_toggle"); request.putInt("slot",id); request.putBoolean("enabled",value); simAction(request);
                });
                jump(category,"slot"+slot+".save","保存或更新 PIN",()->dialogs.pin(id,(pin,confirmation)->{
                    Bundle request=command("sim_save"); request.putInt("slot",id);
                    request.putString("pin",pin); request.putString("confirmation",confirmation);
                    try { simAction(request); } finally { request.remove("pin"); request.remove("confirmation"); }
                }));
                jump(category,"slot"+slot+".clear","清除此卡配置",()->dialogs.confirm("清除此卡配置", "将删除卡槽 "+(id+1)+" 的已保存 PIN 和暂存配置。",()->{
                    Bundle request=command("sim_clear"); request.putInt("slot",id); simAction(request);
                }));
            }
            Object management=category("management",null);
            jump(management,"sim_clear_all","清除所有卡槽配置",()->dialogs.confirm("清除所有卡槽配置","将删除两个卡槽的已保存 PIN 和暂存配置。",()->simAction(command("sim_clear_all"))));
        } else {
            SettingsCatalog.Group group=SettingsCatalog.find(route,context);
            if(group==null) throw new IllegalArgumentException("Unknown page");
            Object master=null;
            if(pageRoute.equals(route)){
                master=category("master",null);
                toggle(master,"enabled",SettingsCatalog.masterTitle(group),group.summary(),value->put(route,"enabled",value));
            }
            var presentation=SettingsLayout.page(pageRoute);
            if(!pageRoute.equals(route)){
                Object parent=jump(information,"parent_status",group.title()+"增强",()->open(route));
                host.call(parent,"setSummary","点按返回此功能的总设置");
            }
            if(presentation!=null && !presentation.demo().isEmpty()){
                Object preview=jump(category("preview",null),"preview","查看效果演示",
                        ()->dialogs.demo(presentation.demo(),pageRoute,presentation.title(),presentation.summary(),valueMap(route)));
                host.call(preview,"setSummary","系统效果模拟 · 点按预览当前选项，不会修改设置");
            }
            Object pages=null;
            for(var child:SettingsLayout.children(pageRoute)){
                if(child.route().equals("sim_slots"))continue;
                if(pages==null)pages=category("pages","功能分类");
                Object target=jump(pages,"open_"+child.route(),child.title(),()->open(child.route()));
                host.call(target,"setSummary",child.summary());
            }
            if("installer".equals(route))jump(category("installer_target","安装器"),"target_package","选择安装器",this::chooseInstaller);
            if("icons".equals(pageRoute))jump(category("bluetooth_rules","蓝牙"),"bluetooth_mode","蓝牙显示规则",this::chooseBluetoothRule);
            if("icons".equals(pageRoute))jump(category("icon_order","图标位置"),"edit_order","编辑左右区域与顺序",this::editIconOrder);
            String section=null; Object current=master;
            for(SettingsCatalog.Item item:SettingsLayout.items(context,pageRoute)) {
                if(!item.section().equals(section)) {
                    section=item.section(); current=category("section_"+section,section);
                    controls.put("section_"+section,current);
                }
                if(!item.valueOnly())toggle(current,item.key(),item.title(),item.description(),value->put(route,item.key(),value));
                if(item.adjustable()) {
                    Object numeric=preference(current,NUMBER,item.valueKey(),item.title()+(item.valueOnly()?"":"数值"));
                    Runnable manual=()->{
                        long revision=numberState(item).revision();
                        dialogs.number(item,number(route,item.valueKey(),item.valueDefault()),value->saveNumber(item,value,revision));
                    };
                    host.listen(numeric,"setOnPreferenceClickListener",args->{if(active)manual.run();return true;});
                    numberRows.put(numeric,new NativeNumberRow(item,()->numberState(item),(value,revision)->saveNumber(item,value,revision),manual));
                }
            }
            if ("cloud_media".equals(pageRoute)) {
                Object waveParameters = controls.get("section_媒体实时律动参数");
                jump(waveParameters, MediaWaveBands.KEY, SettingsCatalog.WAVE_BANDS_TITLE, this::editWaveBands);
                jump(waveParameters, "reset_wave", SettingsCatalog.WAVE_RESET_TITLE, this::resetWave);
            }
            Object management=null;
            if(pageRoute.equals(route)||"icons_visibility".equals(pageRoute))management=category("management",null);
            if("installer".equals(route)) {
                Object help=jump(management,"installer_help","使用说明",()->{});
                host.call(help,"setSummary",SettingsCatalog.INSTALLER_HELP);
                host.call(help,"setSelectable",false);
            }
            if("icons".equals(route)) {
                Object help=jump(management,"icons_help","显示说明",()->{});
                host.call(help,"setSummary","仅下拉后显示可逐项设置，包含时钟和电池，不影响应用通知小图标。蓝牙规则可与下拉开关同时使用；仅在系统当前需要显示时出现。锁屏默认沿用隐藏规则，可单独关闭；锁屏大时钟与息屏保持系统处理。更新模块后重启系统界面加载，之后可即时调整。");
                host.call(help,"setSelectable",false);
            }
            if("sim".equals(route)) jump(management,"sim_slots","管理卡槽与 PIN",()->open("sim_slots"));
            else if(pageRoute.equals(route)) jump(management,"reset","恢复本组默认设置",()->confirmReset(route));
        }
        host.call(fragment,"setPreferenceScreen",screen);
        render();
    }
    private Object category(String key,String title) throws ReflectiveOperationException {
        Object category=host.create(CATEGORY,context);
        host.call(category,"setKey","coloros."+key); host.call(category,"setPersistent",false);
        if(title!=null) host.call(category,"setTitle",title);
        host.call(screen,"addPreference",category); return category;
    }
    private Object preference(Object parent,String type,String key,String title) throws ReflectiveOperationException {
        Object preference=JUMP.equals(type) ? host.summaryJump(context) : NUMBER.equals(type) ? host.numberPreference(context) : host.create(type,context);
        host.call(preference,"setKey",key); host.call(preference,"setTitle",title); host.call(preference,"setPersistent",false);
        host.call(parent,"addPreference",preference); controls.put(key,preference); return preference;
    }
    private Object jump(Object parent,String key,String title,Runnable click) throws ReflectiveOperationException {
        Object preference=preference(parent,JUMP,key,title);
        host.listen(preference,"setOnPreferenceClickListener",args->{ if(active) click.run(); return true; }); return preference;
    }
    private void toggle(Object parent,String key,String title,String summary,Consumer<Boolean> change) throws ReflectiveOperationException {
        Object preference=preference(parent,SWITCH,key,title);
        if(summary!=null) host.call(preference,"setSummary",summary);
        host.listen(preference,"setOnPreferenceChangeListener",args->{
            if(active && args.length>1 && args[1] instanceof Boolean value) change.accept(value);
            return false;
        });
    }
    void start() {
        if(active) return;
        // Retain the acknowledged presentation while reconnecting. Clearing it changes row
        // visibility/heights during the back transition; cached values are read-only until refreshed.
        active=true; int epoch=++generation; freshGroups.clear(); numberPreviews.clear(); readNotices.clear(); simLoaded=false; notice="正在连接…"; render();
        try {
            Object owner=host.call(fragment,"getActivity");Object list=host.call(fragment,"getListView");
            if(owner instanceof Activity activity && list instanceof android.view.ViewGroup group){
                if(pageInsets!=null && pageInsets.owns(group))pageInsets.resume();
                else {releaseView();pageInsets=new NativePageInsets(group,activity.getWindow().getDecorView(),!insetsInitialized);insetsInitialized=true;}
            }
        }catch(ReflectiveOperationException|RuntimeException unavailable){ /* Older host retains its own padding. */ }
        backend=factory.create(context,new SettingsBridgeClient.Listener() {
            public void onConnectionChanged(boolean connected) {
                if(!active || epoch!=generation) return;
                if(connected) { notice=""; refresh(true); }
                else {
                    if ("cloud_media".equals(pageRoute)) dialogs.clear();
                    notice="连接不可用，点按重试。请确认模块已启用。"; freshGroups.clear(); states.clear(); numberPreviews.clear(); managementState=null; simLoaded=false; busy.clear(); visibleBusy.clear(); busyTickets.clear(); render();
                }
            }
            public void onChanged() {
                if(!active || epoch!=generation || refreshQueued) return;
                refreshQueued=true;
                main.post(()-> { if(!active || epoch!=generation) return; refreshQueued=false; refresh(false); });
            }
        });
        backend.connect();
    }
    void stop() {
        active=false; generation++; refreshQueued=false; freshGroups.clear(); busy.clear(); numberPreviews.clear(); visibleBusy.clear(); busyTickets.clear(); reading.clear(); readAgain.clear();
        dialogs.clear(); if(backend!=null) backend.close(); backend=null; render();
        if(pageInsets!=null)pageInsets.pause();
    }
    void releaseView(){if(pageInsets!=null){pageInsets.close();pageInsets=null;}}
    void clearInputs() { dialogs.clear(); }
    void bindNumber(Object preference,android.view.ViewGroup row){
        NativeNumberRow binding=numberRows.get(preference);if(binding!=null)binding.bind(row,host);
    }
    private NativeNumberRow.State numberState(SettingsCatalog.Item item){
        Bundle snapshot=states.get(route);
        Map<String,Object> values=valueMap(route);
        boolean allowed=displayedWritable(route) && SettingsCatalog.controlEnabled(route,item.key(),values) && item.valueEnabled(values);
        return new NativeNumberRow.State(displayedNumber(item),allowed,snapshot==null?-1:snapshot.getLong("revision"));
    }
    private int displayedNumber(SettingsCatalog.Item item){
        // Preserve the gesture's target while the bridge is saving; only the reply confirms it.
        Integer preview=busy.contains(route) ? numberPreviews.get(item.valueKey()) : null;
        return preview==null ? number(route,item.valueKey(),item.valueDefault()) : preview;
    }
    private void saveNumber(SettingsCatalog.Item item,int value,long revision){
        NativeNumberRow.State current=numberState(item);
        if(!current.writable() || !writable(route))return;
        if(current.revision()!=revision){message(explain("conflict"));render();return;}
        numberPreviews.put(item.valueKey(),value);
        put(route,item.valueKey(),value);
    }
    private void retry() {
        if(backend!=null && backend.isConnected()) { notice=""; refresh(true); }
        else { stop(); start(); }
    }
    private void open(String target) {
        Intent intent=SettingsRoutes.intent(target);
        if(!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(intent); } catch(RuntimeException failure) { message("无法打开此页面，请使用独立应用。"); }
    }
    private boolean connected() { return active && backend!=null && backend.isConnected(); }
    private List<String> groups() {
        if(!"home".equals(route)) return List.of(route);
        List<String> groups=new ArrayList<>();
        for(SettingsCatalog.Group group:SettingsCatalog.GROUPS) groups.add(group.key());
        groups.add("integration"); return groups;
    }
    private void refresh(boolean requestPhoneStatus) {
        if(!connected()) return;
        if("sim_slots".equals(route)) readSim(requestPhoneStatus);
        else for(String group:groups()) read(group);
        if("home".equals(route)) readManagement();
    }
    private void readManagement() {
        if(!reading.add("management")) { readAgain.add("management"); return; }
        send(command("management_status"),response->{
            reading.remove("management"); boolean current=receiveManagement(response);
            String status=response.getString("status","failed");
            if(!"ok".equals(status)) readNotices.put("management",explain(status));
            else if(current) readNotices.remove("management");
            else if(managementState==null) readNotices.put("management",explain("failed"));
            render();
            if(readAgain.remove("management")) readManagement();
        });
    }
    private boolean receiveManagement(Bundle response) {
        if(response.containsKey("launcher_visible") && response.containsKey("management_revision")) {
            if(!freshGroups.contains("management") || managementState==null || response.getLong("management_revision")>=managementState.getLong("management_revision")) {
                managementState=new Bundle(response); freshGroups.add("management"); return true;
            }
        } else {managementState=null;freshGroups.remove("management");}
        return false;
    }
    private void managementAction(Bundle request) {
        if(!connected() || !freshGroups.contains("management") || managementState==null || busy.contains("management")) return;
        if("restart_scopes".equals(request.getString("op")) && managementState.getBoolean("restarting")) return;
        beginBusy("management","正在处理…");
        send(request,response->{
            finishBusy("management"); receiveManagement(response);
            String status=response.getString("status","failed");
            notice=Set.of("ok","accepted","busy").contains(status)?"":explain(status);
            render();
        });
    }
    private void read(String group) {
        if(!reading.add(group)) { readAgain.add(group); return; }
        Bundle request=command("snapshot"); request.putString("group",group);
        send(request,response->{
            reading.remove(group);
            boolean current=receiveConfig(group,response);
            String status=response.getString("status","failed");
            if(!"ok".equals(status)) readNotices.put(group,explain(status));
            else if(current) readNotices.remove(group);
            render();
            if(readAgain.remove(group)) read(group);
        });
    }
    private boolean receiveConfig(String group,Bundle response) {
        Bundle previous=freshGroups.contains(group)?states.get(group):null;
        if(response.containsKey("revision") && response.getBundle("values")!=null) {
            if(previous==null || response.getLong("revision")>=previous.getLong("revision")) {
                states.put(group,new Bundle(response)); freshGroups.add(group); return true;
            }
        } else {states.remove(group);freshGroups.remove(group);}
        return false;
    }
    private boolean writable(String group) {
        Bundle state=states.get(group);
        return connected() && freshGroups.contains(group) && !busy.contains(group) && state!=null && state.getBoolean("writable");
    }
    private boolean displayedWritable(String group) {
        Bundle state=states.get(group);
        return connected() && freshGroups.contains(group) && !visibleBusy.contains(group) && state!=null && state.getBoolean("writable");
    }
    private Bundle values(String group) { Bundle state=states.get(group); return state==null ? null : state.getBundle("values"); }
    private boolean flag(String group,String key,boolean fallback) { Bundle values=values(group); return values==null ? fallback : values.getBoolean(key,fallback); }
    private int number(String group,String key,int fallback) { Bundle values=values(group); return values==null ? fallback : values.getInt(key,fallback); }
    private void put(String group,String key,Object value) {
        if(!writable(group)) return;
        Bundle request=command("put"); request.putString("group",group); request.putString("key",key);
        request.putLong("revision",states.get(group).getLong("revision"));
        if(value instanceof Boolean v) request.putBoolean("value",v);
        else if(value instanceof String v)request.putString("value",v);else request.putInt("value",(Integer)value);
        write(group,request);
    }
    private void chooseInstaller() {
        if(!writable("installer"))return;
        long revision=states.get("installer").getLong("revision");
        String selected=values("installer").getString("target_package","");
        beginBusy("installer","正在读取安装器…");
        send(command("installer_choices"),response->{
            finishBusy("installer");render();
            String[] packages=response.getStringArray("packages"),names=response.getStringArray("labels");
            if(!"ok".equals(response.getString("status")) || packages==null || names==null || packages.length!=names.length) {
                message("无法读取安装器列表，请重试。");return;
            }
            CharSequence[] labels=new CharSequence[names.length+1];labels[0]="跟随系统（不替换）";
            int checked=0;for(int i=0;i<names.length;i++){labels[i+1]=names[i];if(packages[i].equals(selected))checked=i+1;}
            dialogs.choice("选择安装器",labels,checked,index->{
                if(!writable("installer") || index<0 || index>packages.length)return;
                Bundle request=command("put");request.putString("group","installer");request.putString("key","target_package");
                request.putString("value",index==0?"":packages[index-1]);request.putLong("revision",revision);write("installer",request);
            });
        });
    }
    private void chooseBluetoothRule() {
        if(!writable("icons") || !flag("icons","enabled",false))return;
        long revision=states.get("icons").getLong("revision");
        int mode=dev.howard.colorosenhance.icons.StatusIconConfig.from(valueMap("icons")).bluetoothMode();
        dialogs.choice("蓝牙显示规则",dev.howard.colorosenhance.icons.StatusIconConfig.BLUETOOTH_LABELS,mode,index->{
            if(!writable("icons"))return;
            Bundle request=command("put");request.putString("group","icons");request.putString("key","bluetooth_mode");
            request.putInt("value",index);request.putLong("revision",revision);write("icons",request);
        });
    }
    private void editIconOrder(){
        if(!writable("icons"))return;
        long revision=states.get("icons").getLong("revision");
        var config=dev.howard.colorosenhance.icons.StatusIconConfig.from(valueMap("icons"));
        dialogs.iconOrder(config.order(),dev.howard.colorosenhance.icons.StatusIconCatalog.slots(context),encoded->{
            if(!writable("icons"))return;
            Bundle request=command("put");request.putString("group","icons");request.putString("key",dev.howard.colorosenhance.icons.IconOrder.KEY);
            request.putString("value",encoded);request.putLong("revision",revision);write("icons",request);
        });
    }
    private void editWaveBands() {
        Map<String,Object> values = valueMap(route);
        if (!writable(route) || !SettingsCatalog.controlEnabled(route, MediaWaveBands.KEY, values)) return;
        long revision = states.get(route).getLong("revision");
        dialogs.waveBands(MediaWaveBands.from(values), encoded -> {
            if (!writable(route)) return;
            if (states.get(route).getLong("revision") != revision) {
                message(explain("conflict"));
                return;
            }
            if (SettingsCatalog.controlEnabled(route, MediaWaveBands.KEY, valueMap(route))) {
                put(route, MediaWaveBands.KEY, encoded);
            }
        });
    }
    private void resetWave() {
        if (!writable(route) || !SettingsCatalog.controlEnabled(route, MediaWaveBands.KEY, valueMap(route))) return;
        Bundle request = command("reset_wave");
        request.putString("group", route);
        request.putLong("revision", states.get(route).getLong("revision"));
        write(route, request);
    }
    private void confirmReset(String group) {
        if(!writable(group))return;
        long revision=states.get(group).getLong("revision");
        dialogs.confirm("恢复本组默认设置","本组总开关将关闭，其余参数恢复默认值。",()->reset(group,revision));
    }
    private void reset(String group,long revision) {
        if(!writable(group)) return;
        if(states.get(group).getLong("revision")!=revision){message(explain("conflict"));return;}
        Bundle request=command("reset"); request.putString("group",group); request.putLong("revision",revision); write(group,request);
    }
    private void write(String group,Bundle request) {
        beginBusy(group,"正在保存…");
        send(request,response->{
            finishBusy(group);
            String status=response.getString("status","failed");
            notice="ok".equals(status) ? "" : explain(status);
            receiveConfig(group,response); render();
        });
    }
    private void beginBusy(String group,String message) {
        busy.add(group);
        long ticket=++nextBusyTicket; busyTickets.put(group,ticket);
        int epoch=generation;
        // Serialize immediately, but avoid a whole-page disabled/enabled flash for fast replies.
        main.postDelayed(()->{
            if(!active || epoch!=generation || !Objects.equals(busyTickets.get(group),ticket)) return;
            visibleBusy.add(group); notice=message; render();
        },250);
    }
    private void finishBusy(String group) {
        busy.remove(group); visibleBusy.remove(group); busyTickets.remove(group);
        if(route.equals(group))numberPreviews.clear();
    }
    private void readSim(boolean phone) {
        if(!reading.add("sim_slots")) { readAgain.add("sim_slots"); return; }
        Bundle request=command("sim_status"); request.putBoolean("refresh",phone);
        send(request,response->{
            reading.remove("sim_slots"); receiveSim(response); render();
            if(readAgain.remove("sim_slots")) readSim(false);
        });
    }
    private void simAction(Bundle request) {
        if(!connected() || !simLoaded || busy.contains("sim_slots")) return;
        beginBusy("sim_slots","正在处理…");
        send(request,response->{ finishBusy("sim_slots"); receiveSim(response); render(); });
    }
    private void receiveSim(Bundle response) {
        String status=response.getString("status","failed");
        simLoaded=("ok".equals(status)||"accepted".equals(status)) && response.getBundle("slot0")!=null && response.getBundle("slot1")!=null;
        if(simLoaded) { simState=new Bundle(response); notice=response.getString("message",""); }
        else notice=explain(status);
    }
    private void send(Bundle request,Consumer<Bundle> reply) {
        int epoch=generation;
        backend.request(request,response->{
            if(!active || epoch!=generation) return;
            reply.accept(response==null ? failed() : response);
        });
    }
    private void render() {
        if(screen==null) return;
        try {
            String state=notice;
            if(state.isEmpty() && !readNotices.isEmpty()) state=readNotices.values().iterator().next();
            if(state.isEmpty()) state=connected() ? "设置与独立应用同步" : "连接已断开，点按重试。";
            summary(statusRow,state);
            if("home".equals(route)) {
                for(SettingsCatalog.Group group:SettingsCatalog.GROUPS) {
                    Bundle snapshot=states.get(group.key());
                    String summary=snapshot==null?"正在读取…":!snapshot.getBoolean("writable")?"保存状态未确认":flag(group.key(),"enabled",false)?"已开启":"已关闭";
                    summary(controls.get("open_"+group.key()),summary+" · "+group.summary());
                }
                checked("integration.show_entry",flag("integration","show_entry",true)); enabled("integration.show_entry",displayedWritable("integration"));
                boolean managementReady=connected() && freshGroups.contains("management") && managementState!=null && !visibleBusy.contains("management");
                checked("launcher_visible",managementState==null || managementState.getBoolean("launcher_visible"));
                enabled("launcher_visible",managementReady);
                enabled("restart_scopes",managementReady && !managementState.getBoolean("restarting"));
                summary(controls.get("restart_scopes"),managementState==null?readNotices.getOrDefault("management","正在读取…"):
                        managementState.getString("restart_message","").isEmpty()?"系统界面、电话服务和设置 · 需要 root":managementState.getString("restart_message"));
            } else if("sim_slots".equals(route)) renderSim();
            else {
                boolean writable=displayedWritable(route);
                checked("enabled",flag(route,"enabled",false)); enabled("enabled",writable);
                summary(controls.get("parent_status"),!states.containsKey(route)?"正在读取状态 · 点按返回总设置":
                        !states.get(route).getBoolean("writable")?"保存状态未确认 · 点按返回总设置":
                        flag(route,"enabled",false)?"增强已开启 · 点按返回总设置":"增强尚未开启 · 点按返回总设置开启");
                if("installer".equals(route)) {
                    enabled("target_package",writable);
                    Bundle stateBundle=states.get(route);
                    summary(controls.get("target_package"),stateBundle==null?"正在读取…":stateBundle.getString("target_label","未选择 · 跟随系统"));
                }
                if("icons".equals(route)) {
                    enabled("edit_order",writable);
                    summary(controls.get("edit_order"),"长按拖动，支持跨左右区域移动，包括时钟与电池。确认后保存；开启自定义排序后应用。");
                    enabled("bluetooth_mode",writable && flag(route,"enabled",false));
                    int mode=dev.howard.colorosenhance.icons.StatusIconConfig.from(valueMap(route)).bluetoothMode();
                    summary(controls.get("bluetooth_mode"),dev.howard.colorosenhance.icons.StatusIconConfig.BLUETOOTH_LABELS[mode]);
                }
                if(pageRoute.equals("cloud_scenes"))visible("open_cloud_landscape",!flag(route,"landscape_follow_portrait",false));
                for(SettingsCatalog.Item item:SettingsLayout.items(context,pageRoute)) {
                    Map<String,Object> values=valueMap(route);
                    boolean masterEnabled=SettingsCatalog.controlEnabled(route,item.key(),values);
                    if(!item.valueOnly())checked(item.key(),flag(route,item.key(),item.defaultFlag()));
                    visible(item.key(),item.visible(values));
                    enabled(item.key(),writable && masterEnabled);
                    summary(controls.get(item.key()),item.summary(values));
                    if(item.adjustable()) {
                        visible(item.valueKey(),item.visible(values));
                        enabled(item.valueKey(),writable && masterEnabled && item.valueEnabled(values));
                        summary(controls.get(item.valueKey()),(item.valueOnly()?item.description()+"\n":"")+NativeDialogs.format(displayedNumber(item),item.divisor())+" "+item.unit());
                    }
                }
                if ("cloud_media".equals(pageRoute)) {
                    Map<String,Object> values = valueMap(route);
                    boolean waveVisible = flag(route, "realtime_media_wave", false);
                    boolean waveWritable = writable && SettingsCatalog.controlEnabled(route, MediaWaveBands.KEY, values);
                    visible(MediaWaveBands.KEY, waveVisible);
                    enabled(MediaWaveBands.KEY, waveWritable);
                    summary(controls.get(MediaWaveBands.KEY), SettingsCatalog.waveBandsSummary(MediaWaveBands.from(values)));
                    visible("reset_wave", waveVisible);
                    enabled("reset_wave", waveWritable);
                    summary(controls.get("reset_wave"), SettingsCatalog.WAVE_RESET_HELP);
                }
                for(NativeNumberRow row:numberRows.values())row.update();
                SettingsCatalog.Group currentGroup=SettingsCatalog.find(route,context);
                for(String section:SettingsLayout.items(context,pageRoute).stream().map(SettingsCatalog.Item::section).distinct().toList())
                    visible("section_"+section,SettingsCatalog.sectionVisible(currentGroup,section,valueMap(route)));
                if(!"sim".equals(route)) enabled("reset",writable);
            }
        } catch(ReflectiveOperationException | RuntimeException failure) { Toast.makeText(context,"此系统版本的设置控件不可用，请使用独立应用。",Toast.LENGTH_LONG).show(); }
    }
    private void renderSim() throws ReflectiveOperationException {
        boolean available=connected() && simLoaded && !visibleBusy.contains("sim_slots");
        boolean anyClear=false,anyBusy=false;
        for(int slot=0;slot<2;slot++) {
            Bundle data=simState==null ? null : simState.getBundle("slot"+slot);
            boolean busy=data!=null && data.getBoolean("busy"); anyBusy|=busy;
            String summary=data==null ? "正在读取…" : data.getString("message");
            if(summary==null || summary.isEmpty()) summary=data==null ? "正在读取…" : data.getString("summary","");
            if(data!=null && data.getBoolean("pending")) summary+="\n首次导入完成前请勿换卡或交换卡槽。";
            if(busy) summary+="\n正在处理…";
            summary(controls.get("slot"+slot+".status"),summary);
            checked("slot"+slot+".enabled",data!=null && data.getBoolean("enabled"));
            enabled("slot"+slot+".enabled",available && !busy && data!=null && data.getBoolean("can_toggle"));
            enabled("slot"+slot+".save",available && !busy);
            boolean canClear=data!=null && data.getBoolean("can_clear"); anyClear|=canClear;
            enabled("slot"+slot+".clear",available && !busy && canClear);
        }
        enabled("sim_clear_all",available && anyClear && !anyBusy);
    }
    private void checked(String key,boolean value) throws ReflectiveOperationException {
        Object control=controls.get(key);
        if(control==null)return;
        if(Boolean.TRUE.equals(host.call(control,"isChecked"))!=value) host.call(control,"setChecked",value);
    }
    private void enabled(String key,boolean value) throws ReflectiveOperationException {
        Object control=controls.get(key);
        if(control==null)return;
        if(Boolean.TRUE.equals(host.call(control,"isEnabled"))!=value) host.call(control,"setEnabled",value);
    }
    private void visible(String key,boolean value) throws ReflectiveOperationException {
        Object control=controls.get(key);
        if(control==null)return;
        if(Boolean.TRUE.equals(host.call(control,"isVisible"))!=value) host.call(control,"setVisible",value);
    }
    private void summary(Object control,String value) throws ReflectiveOperationException {
        if(control==null)return;
        if(!android.text.TextUtils.equals((CharSequence)host.call(control,"getSummary"),value)) host.call(control,"setSummary",value);
    }
    private Map<String,Object> valueMap(String group) {
        Bundle values=values(group);
        if(values==null) return Map.of();
        Map<String,Object> result=new HashMap<>();
        for(String key:values.keySet()) result.put(key,values.get(key));
        return result;
    }
    private void message(String message) { notice=message; render(); }
    private static Bundle command(String op) { Bundle request=new Bundle(); request.putString("op",op); return request; }
    private static Bundle failed() { Bundle result=new Bundle(); result.putString("status","failed"); return result; }
    private static String explain(String status) {
        return switch(status) {
            case "conflict" -> "配置已在另一页面更改，已读取最新值，请重新操作。";
            case "uncertain" -> "保存状态未确认，已暂停修改；请重新连接配置服务后重试。";
            case "timeout" -> "处理超时，结果未确认。点按刷新后检查实际状态。";
            case "unavailable" -> "配置服务不可用，点按重试。请确认模块已启用。";
            case "invalid" -> "设置参数无效，未保存。";
            default -> "操作未完成，请刷新后检查状态。";
        };
    }
}
