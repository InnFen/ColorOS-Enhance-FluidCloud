package dev.howard.colorosenhance.installer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.net.Uri;
import java.util.*;
import java.util.function.Function;

public final class InstallerChoices {
    public record Choice(String packageName,String label) {
        public String display(){return label+"\n"+packageName;}
    }
    @FunctionalInterface interface Query {List<ResolveInfo> resolve(Intent intent);}
    public static List<Choice> list(Context context) {
        PackageManager pm=context.getPackageManager();
        return discover(i->pm.queryIntentActivities(i,PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY)),
                r->r.activityInfo.applicationInfo.loadLabel(pm).toString(),android.os.Process.myUid()/100000);
    }
    static List<Choice> discover(Query query,Function<ResolveInfo,String> label,int user) {
        Map<String,Choice> apps=new HashMap<>();
        for(String action:List.of(Intent.ACTION_VIEW,Intent.ACTION_INSTALL_PACKAGE)) {
            for(String scheme:List.of("content","file")) {
                Intent probe=new Intent(action).setDataAndType(Uri.parse(scheme+":///coloros-enhance.apk"),InstallerRouting.APK);
                for(ResolveInfo result:query.resolve(probe)) {
                    if(result==null || result.activityInfo==null)continue;
                    ActivityInfo activity=result.activityInfo;ApplicationInfo app=activity.applicationInfo;
                    if(!activity.enabled || !activity.exported || (activity.permission!=null && !activity.permission.isEmpty())
                            || app==null || !app.enabled || app.uid<0 || app.uid/100000!=user
                            || (app.flags&ApplicationInfo.FLAG_SUSPENDED)!=0
                            || !Objects.equals(activity.packageName,app.packageName)
                            || !InstallerPreferences.validPackage(activity.packageName) || activity.packageName.isEmpty()
                            || InstallerRouting.STOCK.equals(activity.packageName))continue;
                    String name=label.apply(result);
                    apps.putIfAbsent(activity.packageName,new Choice(activity.packageName,name==null || name.isBlank()?activity.packageName:name));
                }
            }
        }
        return apps.values().stream().sorted(Comparator.comparing(Choice::label).thenComparing(Choice::packageName)).toList();
    }
    public static String describe(Context context,String selected) {
        if(selected==null || selected.isEmpty())return "未选择 · 跟随系统";
        try {
            ApplicationInfo app=context.getPackageManager().getApplicationInfo(selected,
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS));
            String suffix=app.enabled && (app.flags&ApplicationInfo.FLAG_SUSPENDED)==0 ? "" : "（已停用，使用系统流程）";
            return app.loadLabel(context.getPackageManager())+suffix+"\n"+selected;
        } catch(PackageManager.NameNotFoundException | RuntimeException failure) {return selected+"（不可用，使用系统流程）";}
    }
    private InstallerChoices() {}
}
