package dev.howard.colorosenhance.installer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import java.util.List;
import java.util.Comparator;

/** Policy boundary for native Activity resolution; never starts an Activity. */
public final class InstallerRouting {
    public static final String STOCK = "com.android.packageinstaller";
    public static final String APK = "application/vnd.android.package-archive";
    public record Origin(int uid, String packageName, boolean pendingIntent) {}
    @FunctionalInterface public interface Resolver {
        ResolveInfo resolve(Intent intent) throws Throwable;
        default List<ResolveInfo> query(Intent intent) throws Throwable {return List.of();}
    }

    public static ResolveInfo route(boolean enabled, String target, Intent intent, String type, int user,
                                    Origin origin, ResolveInfo original, Resolver resolver) {
        if (!enabled || !InstallerPreferences.validPackage(target) || target.isEmpty() || STOCK.equals(target)) return original;
        try {
            if (!eligible(intent,type,user,origin,target) || !stockEntry(original)) return original;
            // Work on a copy until the native resolver has proved the replacement available.
            // Package-restricted implicit resolution honors this APK's action, MIME and URI scheme.
            Intent candidate = new Intent(intent).setComponent(null).setPackage(target);
            ResolveInfo replacement = resolver.resolve(candidate);
            if (replacement!=null && replacement.activityInfo!=null
                    && "android".equals(replacement.activityInfo.packageName)
                    && "com.android.internal.app.ResolverActivity".equals(replacement.activityInfo.name)) {
                ResolveInfo handler=resolver.query(candidate).stream().filter(r->available(r,user,target))
                        .sorted(Comparator.<ResolveInfo>comparingInt(r->r.priority).reversed()
                                .thenComparing(Comparator.<ResolveInfo>comparingInt(r->r.preferredOrder).reversed())
                                .thenComparing(Comparator.<ResolveInfo>comparingInt(r->r.isDefault?1:0).reversed())
                                .thenComparing(Comparator.<ResolveInfo>comparingInt(r->r.match).reversed())
                                .thenComparing(r->r.activityInfo.name)).findFirst().orElse(null);
                if(handler==null)return original;
                ComponentName selected=new ComponentName(target,handler.activityInfo.name);
                replacement=resolver.resolve(new Intent(candidate).setComponent(selected));
                if(replacement==null || replacement.activityInfo==null
                        || !selected.getClassName().equals(replacement.activityInfo.name))return original;
            }
            if (!available(replacement,user,target)) return original;
            intent.setPackage(null).setComponent(new ComponentName(target,replacement.activityInfo.name));
            return replacement;
        } catch (Throwable failure) {
            // Do not log caller extras/URIs or retry a started Activity. This is only resolution.
            return original;
        }
    }
    private static boolean eligible(Intent intent,String type,int user,Origin origin,String target) {
        if (intent == null || intent.getData() == null || intent.getSelector() != null
                || origin == null || origin.pendingIntent() || origin.uid() < 0 || user < 0
                || origin.uid()/100000 != user || origin.uid()%100000 < 10000
                || origin.packageName() == null || origin.packageName().isEmpty()
                || target.equals(origin.packageName()) || STOCK.equals(origin.packageName())) return false;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_INSTALL_PACKAGE.equals(action)) return false;
        String mime = type != null ? type : intent.getType();
        if (!APK.equals(mime) && !(mime == null && Intent.ACTION_INSTALL_PACKAGE.equals(action))) return false;
        String scheme = intent.getScheme();
        if (!"content".equals(scheme) && !"file".equals(scheme)) return false;
        if (intent.hasExtra("android.content.pm.extra.SESSION_ID") || intent.hasExtra("android.content.pm.extra.SESSION")) return false;
        ComponentName explicit = intent.getComponent();
        if (explicit != null && (!STOCK.equals(explicit.getPackageName())
                || !(STOCK+".InstallStart").equals(explicit.getClassName()))) return false;
        return intent.getPackage() == null || STOCK.equals(intent.getPackage());
    }
    private static boolean stockEntry(ResolveInfo result) {
        return result != null && result.activityInfo != null && STOCK.equals(result.activityInfo.packageName)
                && (STOCK+".InstallStart").equals(result.activityInfo.name);
    }
    private static boolean available(ResolveInfo result,int user,String target) {
        if (result == null || result.activityInfo == null) return false;
        ActivityInfo activity = result.activityInfo;
        ApplicationInfo app = activity.applicationInfo;
        return target.equals(activity.packageName) && activity.name!=null && !activity.name.isEmpty()
                && activity.enabled && activity.exported && (activity.permission == null || activity.permission.isEmpty())
                && app != null && target.equals(app.packageName) && app.enabled && app.uid >= 0
                && app.uid/100000 == user && (app.flags & ApplicationInfo.FLAG_SUSPENDED) == 0;
    }
    private InstallerRouting() {}
}
