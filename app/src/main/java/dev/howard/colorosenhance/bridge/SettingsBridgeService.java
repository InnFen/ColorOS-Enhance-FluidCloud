package dev.howard.colorosenhance.bridge;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import dev.howard.colorosenhance.*;
import com.howard.simpinautounlock.management.SimManagementController;
import java.util.*;
public final class SettingsBridgeService extends Service {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<IBinder,Watch> watches = new LinkedHashMap<>();
    private EnhanceApplication app;
    private SimManagementController sim;
    private boolean destroyed;
    private final Runnable changed = this::publishChange;
    private final SimManagementController.Observer simChanged = state -> publishChange();
    private final Binder endpoint = new Binder() {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(BridgeContract.DESCRIPTOR); return true; }
            if (code != BridgeContract.REQUEST) return super.onTransact(code,data,reply,flags);
            enforceCaller();
            data.enforceInterface(BridgeContract.DESCRIPTOR);
            if (data.dataSize() > 32768) throw new IllegalArgumentException("Request too large");
            Bundle request = data.readBundle(Bundle.class.getClassLoader());
            ResultReceiver receiver = data.readTypedObject(ResultReceiver.CREATOR);
            data.enforceNoDataAvail();
            if (request == null || receiver == null) throw new IllegalArgumentException("Missing request");
            main.post(() -> dispatch(request,receiver));
            if (reply != null) reply.writeNoException();
            return true;
        }
    };
    @Override public void onCreate() { super.onCreate(); app = (EnhanceApplication)getApplication(); app.observe(changed); }
    @Override public IBinder onBind(Intent intent) { return endpoint; }
    @Override public boolean onUnbind(Intent intent) { clearWatches(); return false; }
    @Override public void onDestroy() {
        destroyed = true; clearWatches(); app.removeObserver(changed);
        if (sim != null) sim.removeObserver(simChanged);
        super.onDestroy();
    }
    private void enforceCaller() {
        int caller = Binder.getCallingUid(), own = android.os.Process.myUid(), settingsUid = -1;
        boolean signed = false;
        if (caller != own) {
            try {
                settingsUid = getPackageManager().getApplicationInfo("com.android.settings",0).uid;
                signed = getPackageManager().checkSignatures("android","com.android.settings") == PackageManager.SIGNATURE_MATCH;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        if (!BridgeCallerPolicy.allow(caller,own,settingsUid,signed)) throw new SecurityException("Settings bridge caller denied");
    }
    private void dispatch(Bundle request,ResultReceiver receiver) {
        Bundle response;
        try {
            response = destroyed ? status("unavailable") : execute(request,receiver);
        } catch (RuntimeException error) { response = status("failed"); }
        finally { request.remove("pin"); request.remove("confirmation"); }
        receiver.send(0,response);
    }
    private Bundle execute(Bundle request,ResultReceiver receiver) {
        String op = request.getString("op","");
        String group = request.getString("group");
        switch (op) {
            case "installer_choices": {
                var choices=dev.howard.colorosenhance.installer.InstallerChoices.list(app);
                Bundle response=status("ok");
                response.putStringArray("packages",choices.stream().map(dev.howard.colorosenhance.installer.InstallerChoices.Choice::packageName).toArray(String[]::new));
                response.putStringArray("labels",choices.stream().map(dev.howard.colorosenhance.installer.InstallerChoices.Choice::display).toArray(String[]::new));
                return response;
            }
            case "management_status": return managementState("ok");
            case "launcher_visibility": {
                if (!(request.get("visible") instanceof Boolean visible)) return status("invalid");
                return managementState(app.setLauncherVisible(visible) ? "ok" : "failed");
            }
            case "restart_scopes": {
                if (!Boolean.TRUE.equals(request.get("confirmed"))) return status("invalid");
                return managementState(app.restartScopes() ? "accepted" : "busy");
            }
            case "watch": {
                IBinder token = request.getBinder("token");
                if (token == null || (watches.size() >= 8 && !watches.containsKey(token))) return status("invalid");
                removeWatch(token);
                Watch watch = new Watch(token,receiver);
                try { token.linkToDeath(watch,0); watches.put(token,watch); }
                catch (RemoteException error) { return status("unavailable"); }
                return status("ok");
            }
            case "unwatch": removeWatch(request.getBinder("token")); return status("ok");
            case "snapshot": {
                if (!SettingsCoordinator.knownGroup(group)) return status("invalid");
                app.settings().refresh();
                SettingsCoordinator.State state = app.settings().read(group);
                return config(group,state == null ? "unavailable" : state.writable() ? "ok" : "uncertain",state);
            }
            case "put": {
                Object value = request.get("value");
                SettingsCoordinator.Result result = app.settings().write(group,request.getString("key"),value,request.getLong("revision",-1));
                return config(group,result.status(),result.state());
            }
            case "reset": {
                SettingsCoordinator.Result result = app.settings().reset(group,request.getLong("revision",-1));
                return config(group,result.status(),result.state());
            }
            case "reset_wave": {
                if (!"cloud".equals(group)) return status("invalid");
                SettingsCoordinator.Result result = app.settings().resetWave(request.getLong("revision", -1));
                return config(group, result.status(), result.state());
            }
            case "sim_status": {
                if (request.getBoolean("refresh",false)) sim().refresh();
                return simState("ok");
            }
            case "sim_save", "sim_toggle", "sim_clear": {
                Object slotValue = request.get("slot");
                if (!(slotValue instanceof Integer slot) || slot < 0 || slot > 1) return status("invalid");
                if (op.equals("sim_save")) {
                    String pin = request.getString("pin"), confirmation = request.getString("confirmation");
                    if (pin == null || !pin.matches("[0-9]{4,8}") || !pin.equals(confirmation)) return status("invalid");
                    sim().save(slot,pin,confirmation);
                } else if (op.equals("sim_toggle")) {
                    if (!(request.get("enabled") instanceof Boolean enabled)) return status("invalid");
                    sim().setEnabled(slot,enabled);
                } else sim().clear(slot);
                return simState("accepted");
            }
            case "sim_clear_all": sim().clearAll(); return simState("accepted");
            default: return status("invalid");
        }
    }
    private SimManagementController sim() {
        if (sim == null) { sim = app.simManagement(); sim.addObserver(simChanged); }
        return sim;
    }
    private Bundle managementState(String status) {
        Bundle response=app.managementState(); response.putString("status",status);
        return response;
    }
    private Bundle simState(String status) {
        SimManagementController.Snapshot snapshot = sim().snapshot();
        Bundle result = status(status); result.putString("message",snapshot.message());
        for (SimManagementController.Slot slot : snapshot.slots()) {
            Bundle data = new Bundle();
            data.putString("summary",slot.presentation().displayText());
            data.putBoolean("enabled",slot.presentation().toggleState());
            data.putBoolean("can_toggle",slot.presentation().toggleEnabled());
            data.putBoolean("can_clear",slot.presentation().clearEnabled());
            data.putBoolean("pending",slot.presentation().pendingWarningVisible());
            data.putString("message",slot.message()); data.putBoolean("error",slot.error()); data.putBoolean("busy",slot.busy());
            result.putBundle("slot"+slot.slotId(),data);
        }
        return result;
    }
    private Bundle config(String group,String status,SettingsCoordinator.State state) {
        Bundle response = status(status);
        if (state == null) return response;
        response.putLong("revision",state.revision()); response.putBoolean("writable",state.writable());
        Bundle values = new Bundle();
        state.values().forEach((key,value) -> {
            if (!SettingsCoordinator.validValue(group,key,value)) return;
            if (value instanceof Boolean v) values.putBoolean(key,v);
            else if (value instanceof Integer v) values.putInt(key,v);
            else if (value instanceof String v) values.putString(key,v);
        });
        if("installer".equals(group))response.putString("target_label",dev.howard.colorosenhance.installer.InstallerChoices.describe(app,values.getString("target_package","")));
        response.putBundle("values",values); return response;
    }
    static Bundle status(String value) { Bundle b = new Bundle(); b.putString("status",value); return b; }
    private void publishChange() {
        if (destroyed) return;
        for (Watch watch : new ArrayList<>(watches.values())) watch.receiver.send(0,status("changed"));
    }
    private void removeWatch(IBinder token) {
        Watch old = watches.remove(token);
        if (old != null) old.token.unlinkToDeath(old,0);
    }
    private void clearWatches() { for (IBinder token : new ArrayList<>(watches.keySet())) removeWatch(token); }
    private final class Watch implements IBinder.DeathRecipient {
        final IBinder token; final ResultReceiver receiver;
        Watch(IBinder token,ResultReceiver receiver) { this.token=token; this.receiver=receiver; }
        @Override public void binderDied() { main.post(() -> removeWatch(token)); }
    }
}
