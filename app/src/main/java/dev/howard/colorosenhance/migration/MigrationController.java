package dev.howard.colorosenhance.migration;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import dev.howard.colorosenhance.EnhanceApplication;
import dev.howard.colorosenhance.LauncherVisibility;
import dev.howard.colorosenhance.MainActivity;
import dev.howard.colorosenhance.SettingsCoordinator;
import io.github.libxposed.service.XposedService;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Application-owned, one-time rename migration. No ADB, database edits or silent uninstall. */
public final class MigrationController {
    public enum Phase { IDLE, FRAMEWORK, READING, WAITING_OLD, CONFLICT, FAILED, COMPLETE }
    private final EnhanceApplication app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WeakReference<Activity> foreground=new WeakReference<>(null);
    private volatile Phase phase=Phase.IDLE;
    private volatile String message="正在检查旧版本…";
    private boolean busy, openingMigration;
    public MigrationController(EnhanceApplication app){this.app=app;}
    private SharedPreferences local(){return app.getSharedPreferences("package_migration_v1",Context.MODE_PRIVATE);}
    public Phase phase(){return phase;}
    public String message(){return message;}
    public boolean blocksEditing(){return !local().getBoolean("complete",false);}
    public boolean imported(){return local().getBoolean("imported",false);}
    public boolean pendingPin(){return local().getBoolean("pending_pin",false);}
    public void frameworkChanged(){main.post(()->attempt(false));}
    public void foreground(Activity activity){
        foreground=new WeakReference<>(activity);
        if(activity instanceof MigrationActivity)openingMigration=false;
        attempt(false);showIfNeeded();
    }
    public void background(Activity activity){if(foreground.get()==activity)foreground.clear();}
    public void retry(){attempt(true);}
    public boolean legacyInstalled(){
        try{app.getPackageManager().getApplicationInfo(MigrationProtocol.LEGACY,0);return true;}
        catch(PackageManager.NameNotFoundException absent){return false;}
    }
    private boolean trustedLegacy(){
        return legacyInstalled() && app.getPackageManager().checkSignatures(app.getPackageName(),MigrationProtocol.LEGACY)==PackageManager.SIGNATURE_MATCH;
    }
    private void change(Phase value,String text){phase=value;message=text;app.migrationChanged();showIfNeeded();}
    private void showIfNeeded(){
        Activity activity=foreground.get();
        if(activity instanceof MainActivity && !activity.isFinishing() && blocksEditing()
                && phase!=Phase.COMPLETE && phase!=Phase.IDLE && !openingMigration){
            openingMigration=true;
            try{activity.startActivity(new Intent(activity,MigrationActivity.class));}
            catch(RuntimeException failed){openingMigration=false;throw failed;}
        }
    }
    private void attempt(boolean explicit){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->attempt(explicit));return;}
        if(busy)return;
        if(local().getBoolean("complete",false)){phase=Phase.COMPLETE;message=completionText();return;}
        XposedService service=app.frameworkService();
        if(service==null){
            if(legacyInstalled())change(Phase.FRAMEWORK,"检测到旧版本。请先让 LSPosed 识别新版并打开新版应用，等待配置服务连接；此时新版增强尚未激活。");
            return;
        }
        if(android.os.Process.myUid()/100000!=0){change(Phase.FAILED,"自动迁移仅支持手机主用户，未修改任何旧版数据。");return;}
        if(!legacyInstalled()){
            String saved=local().getString("snapshot",null);
            if(saved!=null){try{finishImport(MigrationProtocol.decode(saved),service);}catch(RuntimeException failure){fail();}return;}
            try{
                if(!service.getRemotePreferences("integration").edit().putBoolean(PackageActivation.READY,true).commit())throw new IllegalStateException();
                if(!local().edit().putBoolean("complete",true).commit())throw new IllegalStateException();
                change(Phase.COMPLETE,"未检测到旧包，使用新版默认设置。");
            }catch(RuntimeException failure){fail();}
            return;
        }
        if(!trustedLegacy()){change(Phase.FAILED,"旧包与新版签名不同，无法安全自动迁移。请勿清除旧数据；先核对安装来源。");return;}
        if(!explicit && (phase==Phase.FAILED || phase==Phase.CONFLICT || local().getBoolean("attempted",false) && !imported())){
            if(phase==Phase.IDLE)change(Phase.FAILED,"上次自动读取未完成。旧数据仍保留，可允许新版 root 授权后重试。");
            return;
        }
        if(foreground.get()==null)return;
        if(!local().edit().putBoolean("attempted",true).commit()){fail();return;}
        boolean overwrite=explicit && phase==Phase.CONFLICT;
        busy=true;change(Phase.READING,"正在自动读取旧版配置。迁移需要新版应用的 root 授权，请处理 KernelSU／root 管理器提示。不会修改旧包或 LSPosed 数据库。");
        new Thread(()->{
            RootMigrationClient.Result result=null;
            try{result=RootMigrationClient.read(app,false);}catch(RuntimeException ignored){}
            RootMigrationClient.Result completed=result;
            main.post(()->{busy=false;
                if(completed==null || !trustedLegacy() || service!=app.frameworkService()){fail();return;}
                try{accept(completed,service,overwrite);}catch(RuntimeException error){fail();}
            });
        },"coloros-package-migration").start();
    }
    private void accept(RootMigrationClient.Result read,XposedService service,boolean explicit){
        MigrationProtocol.Snapshot source=read.snapshot();
        if(source==null)throw new IllegalArgumentException();
        boolean visible=true;
        try{int state=app.getPackageManager().getComponentEnabledSetting(new ComponentName(MigrationProtocol.LEGACY,
                MainActivity.class.getPackageName()+".Launcher"));visible=state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT||state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED;}
        catch(RuntimeException ignored){}
        var snapshot=new MigrationProtocol.Snapshot(source.groups(),source.appearance(),source.glass(),visible,source.pendingPin());
        synchronized(app.settings()){
            var store=store(service);var before=MigrationTransaction.capture(store);
            if(!imported() && !explicit && hasUserSettings(before)){
                change(Phase.CONFLICT,"检测到新版已有设置，尚未覆盖。若确定使用旧版设置，可点击继续迁移；否则保留现有配置。");return;
            }
            String encoded=MigrationProtocol.encode(snapshot);
            if(!local().edit().putString("snapshot",encoded).putBoolean("pending_pin",snapshot.pendingPin()).commit())throw new IllegalStateException();
            if(read.state()==RootMigrationReader.LegacyState.ENABLED){
                var target=activation(snapshot,false);
                if(!MigrationTransaction.apply(store,before,target).success())throw new IllegalStateException();
                if(!local().edit().putBoolean("imported",true).commit())throw new IllegalStateException();
                change(Phase.WAITING_OLD,"设置已自动复制，但检测到旧模块仍在 LSPosed 中启用。新版增强暂不激活。\n\n请在 LSPosed 取消旧包的启用，保留并启用新包，然后返回本页；新版会自动复查并完成交接。先不要清除旧包数据。");
            }else if(read.state()==RootMigrationReader.LegacyState.DISABLED)finishImport(snapshot,service);
            else throw new IllegalStateException();
        }
    }
    static boolean hasUserSettings(Map<String,Map<String,Object>> groups){
        for(var group:groups.entrySet())for(var item:group.getValue().entrySet()){
            if(!SettingsCoordinator.validValue(group.getKey(),item.getKey(),item.getValue()))continue;
            if("installer".equals(group.getKey()) && "target_package".equals(item.getKey()) && "".equals(item.getValue()))continue;
            return true;
        }
        return false;
    }
    static Map<String,Map<String,Object>> activation(MigrationProtocol.Snapshot snapshot,boolean ready){
        var result=new LinkedHashMap<String,Map<String,Object>>();
        for(String group:MigrationProtocol.GROUPS){
            var values=new LinkedHashMap<String,Object>(snapshot.importGroups().get(group));
            if("integration".equals(group)){values.put(PackageActivation.READY,ready);if(!ready)values.put("show_entry",false);}
            else if(!ready)values.put("enabled",false);
            result.put(group,Map.copyOf(values));
        }
        return Map.copyOf(result);
    }
    private void finishImport(MigrationProtocol.Snapshot snapshot,XposedService service){
        synchronized(app.settings()){
            var store=store(service);var outcome=MigrationTransaction.apply(store,MigrationTransaction.capture(store),activation(snapshot,true));
            if(!outcome.success())throw new IllegalStateException();
            if(!app.getSharedPreferences("ui_presentation",Context.MODE_PRIVATE).edit()
                    .putInt("appearance",snapshot.appearance()).putBoolean("liquid_navigation",snapshot.glass()).commit())throw new IllegalStateException();
            if(!local().edit().putBoolean("imported",true).putBoolean("pending_pin",snapshot.pendingPin()).putBoolean("complete",true).commit())throw new IllegalStateException();
            // Do not hide the new launcher while the handoff is still pending.
            boolean launcher=new LauncherVisibility(app).setVisible(snapshot.launcherVisible());
            change(Phase.COMPLETE,completionText()+(launcher?"":"\n桌面图标显隐未能同步，可在管理页手动设置。"));
        }
    }
    private String completionText(){
        if(!imported())return "新版已准备好。";
        return "设置迁移已完成。已确认的 SIM PIN 凭据和当次开机尝试记录仍保留在电话进程中。\n\n请确认 LSPosed 只启用新包及所需作用域，并完整重启一次。确认新版正常后，可卸载旧包。"
                +(pendingPin()?"\n\n检测到尚未确认的暂存 PIN，本次没有导出或复制它，SIM 增强暂时关闭；请在新版重新保存并核对卡槽。":"");
    }
    private void fail(){change(Phase.FAILED,"自动迁移尚未完成，旧包及旧数据未修改。请保留旧包，检查新版的 root 授权和 LSPosed 连接后重试；若仍失败，请反馈模块及框架版本。");}
    private MigrationTransaction.Store store(XposedService service){
        return new MigrationTransaction.Store(){
            private SharedPreferences prefs(String group){if(service!=app.frameworkService())throw new IllegalStateException();return service.getRemotePreferences(group);}
            public Map<String,Object> read(String group){
                var result=new LinkedHashMap<String,Object>();prefs(group).getAll().forEach((key,value)->result.put(key,value instanceof Set<?> set?Set.copyOf(set):value));return result;
            }
            public boolean replace(String group,Map<String,Object> values){
                var editor=prefs(group).edit().clear();values.forEach((key,value)->{
                    if(value instanceof Boolean v)editor.putBoolean(key,v);else if(value instanceof Integer v)editor.putInt(key,v);
                    else if(value instanceof Long v)editor.putLong(key,v);else if(value instanceof Float v)editor.putFloat(key,v);
                    else if(value instanceof String v)editor.putString(key,v);else if(value instanceof Set<?> v){
                        var strings=new java.util.HashSet<String>();for(Object item:v)if(item instanceof String s)strings.add(s);editor.putStringSet(key,strings);
                    }else throw new IllegalArgumentException();
                });return editor.commit();
            }
        };
    }
}
