package dev.howard.colorosenhance.installer;
import android.content.Intent;
import android.content.pm.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class InstallerChoicesTest {
    ResolveInfo app(String pkg,String activity){ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();
        r.activityInfo.packageName=pkg;r.activityInfo.name=activity;r.activityInfo.enabled=true;r.activityInfo.exported=true;
        r.activityInfo.applicationInfo=new ApplicationInfo();r.activityInfo.applicationInfo.packageName=pkg;
        r.activityInfo.applicationInfo.uid=10123;r.activityInfo.applicationInfo.enabled=true;return r;}
    @Test public void discoversApkHandlersAcrossActionsAndSchemesAndDeduplicatesPackages() {
        Set<String> queries=new HashSet<>();
        var choices=InstallerChoices.discover(i->{
            assertEquals(InstallerRouting.APK,i.getType());queries.add(i.getAction()+" "+i.getScheme());
            return List.of(app("one.installer","one.First"),app("one.installer","one.Second"),app("two.installer","two.Install"));
        },r->r.activityInfo.packageName,0);
        assertEquals(List.of("one.installer","two.installer"),choices.stream().map(InstallerChoices.Choice::packageName).toList());
        assertEquals(Set.of(Intent.ACTION_VIEW+" content",Intent.ACTION_VIEW+" file",Intent.ACTION_INSTALL_PACKAGE+" content",Intent.ACTION_INSTALL_PACKAGE+" file"),queries);
    }
    @Test public void excludesDisabledPrivateSuspendedProtectedAndOtherUserActivities() {
        var disabled=app("disabled.app","Install");disabled.activityInfo.enabled=false;
        var privateApp=app("private.app","Install");privateApp.activityInfo.exported=false;
        var stopped=app("stopped.app","Install");stopped.activityInfo.applicationInfo.enabled=false;
        var suspended=app("suspended.app","Install");suspended.activityInfo.applicationInfo.flags|=ApplicationInfo.FLAG_SUSPENDED;
        var permission=app("permission.app","Install");permission.activityInfo.permission="private.permission";
        var work=app("work.app","Install");work.activityInfo.applicationInfo.uid=1010123;
        var allowed=app("valid.app","Install");
        var list=InstallerChoices.discover(i->List.of(disabled,privateApp,stopped,suspended,permission,work,allowed),r->"Name",0);
        assertEquals(1,list.size());assertEquals("valid.app",list.get(0).packageName());
    }
}
