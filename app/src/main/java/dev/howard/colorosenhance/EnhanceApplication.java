package dev.howard.colorosenhance;

import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import com.howard.simpinautounlock.ModuleApplication;
import com.google.android.material.color.DynamicColors;
import io.github.libxposed.service.XposedService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;
import java.util.Set;

public final class EnhanceApplication extends ModuleApplication {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Runnable> observers = new CopyOnWriteArrayList<>();
    private final dev.howard.colorosenhance.updates.LaunchUpdateCheck launchUpdates =
            new dev.howard.colorosenhance.updates.LaunchUpdateCheck(
                    task->new Thread(task,"coloros-update-check").start(),main::post,
                    ()->new dev.howard.colorosenhance.updates.GitHubUpdateClient().check(),BuildConfig.VERSION_CODE,this::notifyViews);
    private final dev.howard.colorosenhance.migration.MigrationController migration = new dev.howard.colorosenhance.migration.MigrationController(this);
    private final SettingsCoordinator settings = new SettingsCoordinator(this::settingsSource,this::notifyViews,
            ()->dev.howard.colorosenhance.icons.StatusIconCatalog.usesColorOsRingerSlot(dev.howard.colorosenhance.icons.StatusIconCatalog.slots(this)));
    boolean restarting;
    SystemUiRestarter.Result restartResult;
    private long managementRevision;
    private final ScopeRestartController scopeRestart=new ScopeRestartController(
            task->new Thread(task,"coloros-scope-restart").start(),main::post,this::runScopeRestart,this::managementChanged);
    public static final String RESTART_CONFIRMATION="将依次重启系统界面、电话服务和设置。状态栏与锁屏会短暂重新加载，电话和移动网络可能暂时中断；请先结束通话。设置将关闭后重新打开。\n\n首次执行需在 KernelSU 中允许 ColorOS 增强的 root 权限。此操作不会重启手机，不能代替 SIM PIN 的完整开机验证，也不能加载系统框架中的安装器替换功能。";
    @Override public void onCreate() {
        super.onCreate(); DynamicColors.applyToActivitiesIfAvailable(this);
        // Fluid-cloud-only build: no automatic upstream update prompt (it would offer the full module).
    }
    @Override protected void onFrameworkServiceChanged(XposedService service) { settings.refresh(); notifyViews(); migration.frameworkChanged(); }
    public dev.howard.colorosenhance.migration.MigrationController migration(){return migration;}
    public dev.howard.colorosenhance.updates.LaunchUpdateCheck launchUpdates(){return launchUpdates;}
    public void migrationChanged(){settings.refresh();notifyViews();}
    public void observe(Runnable observer) { observers.addIfAbsent(observer); }
    public void removeObserver(Runnable observer) { observers.remove(observer); }
    public SettingsCoordinator settings() { return settings; }
    public boolean setLauncherVisible(boolean visible) {
        boolean saved=new LauncherVisibility(this).setVisible(visible); managementChanged(); return saved;
    }
    private void managementChanged() { managementRevision++; notifyViews(); }
    public boolean anyRestarting() { return restarting || scopeRestart.running(); }
    public boolean restartScopes() { return !restarting && scopeRestart.start(); }
    public android.os.Bundle managementState() {
        android.os.Bundle state=new android.os.Bundle();
        state.putLong("management_revision",managementRevision);
        state.putBoolean("launcher_visible",new LauncherVisibility(this).visible());
        state.putBoolean("restarting",anyRestarting());
        state.putString("restart_message",scopeRestart.running()?"正在等待 root 授权与作用域进程重新加载…":
                scopeRestart.result()==null?"":scopeRestart.result().message());
        return state;
    }
    private ScopeRestarter.Outcome runScopeRestart() {
        try(InputStream input=getResources().openRawResource(R.raw.restart_scopes)) {
            return ScopeRestarter.restart(getCacheDir(),new String(input.readAllBytes(),StandardCharsets.UTF_8),android.os.Process.myUid()/100000);
        } catch(Exception failed) { return ScopeRestarter.failure(ScopeRestarter.Code.FAILED); }
    }
    private SettingsCoordinator.Source settingsSource() {
        if (migration.blocksEditing()) return null;
        XposedService service = frameworkService();
        if (service == null) return null;
        return new SettingsCoordinator.Source() {
            public Object identity() { return service; }
            public PreferenceSession.Store open(String group) {
                SharedPreferences prefs = service.getRemotePreferences(group);
                return new PreferenceSession.Store() {
                    public Map<String,?> read() { return prefs.getAll(); }
                    public boolean replace(Map<String,Object> values) {
                        SharedPreferences.Editor editor = prefs.edit().clear();
                        values.forEach((key,value) -> {
                            if (value instanceof Boolean v) editor.putBoolean(key,v);
                            else if (value instanceof Integer v) editor.putInt(key,v);
                            else if (value instanceof Long v) editor.putLong(key,v);
                            else if (value instanceof Float v) editor.putFloat(key,v);
                            else if (value instanceof String v) editor.putString(key,v);
                            else if (value instanceof Set<?> v) {
                                Set<String> strings = new java.util.HashSet<>();
                                for (Object item : v) if (item instanceof String s) strings.add(s);
                                editor.putStringSet(key,strings);
                            }
                        });
                        return editor.commit();
                    }
                };
            }
        };
    }
    private void notifyViews() { main.post(() -> observers.forEach(Runnable::run)); }
    void restartSystemUi() {
        if (anyRestarting()) return;
        restarting = true; restartResult = null; managementChanged();
        new Thread(() -> {
            SystemUiRestarter.Result result;
            try (InputStream input = getResources().openRawResource(R.raw.restart_system_ui)) {
                result = SystemUiRestarter.restart(getCacheDir(), new String(input.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) { result = SystemUiRestarter.Result.FAILED; }
            SystemUiRestarter.Result completed = result;
            main.post(() -> { restarting = false; restartResult = completed; managementChanged(); });
        }, "coloros-systemui-restart").start();
    }
}
