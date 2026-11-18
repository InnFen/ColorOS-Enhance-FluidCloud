package dev.howard.colorosenhance.installer;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static dev.howard.colorosenhance.installer.InstallerRouting.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36, manifest=Config.NONE)
public class InstallerRoutingTest {
    private static final String TARGET="org.example.installer";
    private static final ComponentName COMPONENT=new ComponentName(TARGET,TARGET+".Install");
    private final Origin caller = new Origin(10123,"example.files",false);
    private final ResolveInfo stock = info(STOCK,STOCK+".InstallStart",10020);
    private final ResolveInfo target = info(TARGET,COMPONENT.getClassName(),10234);
    private final AtomicInteger unexpectedResolutions=new AtomicInteger();
    private ResolveInfo unexpectedResolver(Intent intent) { unexpectedResolutions.incrementAndGet(); return target; }
    @After public void noExcludedRequestResolvedATarget() { assertEquals(0,unexpectedResolutions.get()); }
    private Intent apk() { return new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example.files/shared/a.apk"),APK); }
    private ResolveInfo route(Intent intent, Resolver resolver) { return InstallerRouting.route(true,TARGET,intent,APK,0,caller,stock,resolver); }
    private void nativeOnly(Intent intent) { assertSame(stock,route(intent,this::unexpectedResolver)); }
    private static ResolveInfo info(String pkg,String name,int uid) {
        ResolveInfo result=new ResolveInfo(); ActivityInfo activity=new ActivityInfo();
        activity.packageName=pkg; activity.name=name; activity.enabled=true; activity.exported=true;
        activity.applicationInfo=new ApplicationInfo(); activity.applicationInfo.packageName=pkg;
        activity.applicationInfo.enabled=true; activity.applicationInfo.uid=uid; result.activityInfo=activity; return result;
    }
    @Test public void switchesOnlyTheDestinationAndKeepsCallerPayload() {
        Intent intent=apk().setPackage(STOCK).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                .putExtra(Intent.EXTRA_RETURN_RESULT,true).putExtra("custom",42);
        ClipData clip=ClipData.newRawUri("apk",intent.getData()); intent.setClipData(clip);
        Intent before=new Intent(intent); AtomicInteger calls=new AtomicInteger();
        assertSame(target,route(intent,candidate->{
            calls.incrementAndGet(); assertNotSame(intent,candidate); assertNull(candidate.getComponent());
            assertEquals(TARGET,candidate.getPackage()); assertEquals(before.getFlags(),candidate.getFlags());
            assertEquals(before.getData(),candidate.getData()); assertEquals(before.getAction(),candidate.getAction());
            assertTrue(candidate.getBooleanExtra(Intent.EXTRA_RETURN_RESULT,false));
            assertEquals(42,candidate.getIntExtra("custom",0));
            assertEquals(clip.getItemCount(),candidate.getClipData().getItemCount());
            assertEquals(clip.getItemAt(0).getUri(),candidate.getClipData().getItemAt(0).getUri()); return target;
        }));
        assertEquals(1,calls.get()); assertEquals(COMPONENT,intent.getComponent()); assertNull(intent.getPackage());
        assertEquals(before.getFlags(),intent.getFlags()); assertSame(clip,intent.getClipData());
        assertEquals(before.getData(),intent.getData()); assertEquals(before.getType(),intent.getType());
        assertEquals(42,intent.getIntExtra("custom",0)); assertTrue(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT,false));
        assertNull(before.getComponent()); assertEquals(STOCK,before.getPackage());
    }
    @Test public void supportsExplicitStockAndFileInstallRequests() {
        Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(Uri.parse("file:///storage/emulated/0/Download/a.apk"))
                .setComponent(new ComponentName(STOCK,STOCK+".InstallStart"));
        assertSame(target,InstallerRouting.route(true,TARGET,intent,null,0,caller,stock,i->target));
    }
    @Test public void viewUsesResolvedMimeTypeEvenWhenIntentTypeIsAbsent() {
        assertSame(target,route(new Intent(Intent.ACTION_VIEW,apk().getData()),i->target));
    }
    @Test public void disabledNeverCallsResolver() {
        Intent intent=apk(); assertSame(stock,InstallerRouting.route(false,TARGET,intent,APK,0,caller,stock,this::unexpectedResolver));
        assertNull(intent.getComponent());
    }
    @Test public void noSelectionOrInvalidSelectionKeepsNativeFlow() {
        for(String selection:new String[]{null,"","bad/package"})
            assertSame(stock,InstallerRouting.route(true,selection,apk(),APK,0,caller,stock,this::unexpectedResolver));
    }
    @Test public void ambiguousPackageChoosesMatchingHandlerAndRevalidatesWithNativeResolver() {
        ResolveInfo chooser=info("android","com.android.internal.app.ResolverActivity",1000);
        ResolveInfo second=info(TARGET,TARGET+".Second",10234);
        Intent intent=apk();AtomicInteger resolves=new AtomicInteger();
        Resolver resolver=new Resolver(){
            public ResolveInfo resolve(Intent candidate){
                if(resolves.getAndIncrement()==0){assertNull(candidate.getComponent());return chooser;}
                assertEquals(COMPONENT,candidate.getComponent());return target;
            }
            public java.util.List<ResolveInfo> query(Intent candidate){assertNull(candidate.getComponent());assertEquals(TARGET,candidate.getPackage());return java.util.List.of(second,target);}
        };
        assertSame(target,route(intent,resolver));assertEquals(COMPONENT,intent.getComponent());assertEquals(2,resolves.get());
    }
    @Test public void ambiguityQueryFailureOrDifferentExplicitResultCannotCommitDestination() {
        ResolveInfo chooser=info("android","com.android.internal.app.ResolverActivity",1000);
        for(boolean throwsQuery:new boolean[]{false,true}){
            Intent intent=apk();Resolver resolver=new Resolver(){
                public ResolveInfo resolve(Intent candidate){return candidate.getComponent()==null?chooser:info(TARGET,TARGET+".Unexpected",10234);}
                public java.util.List<ResolveInfo> query(Intent candidate){if(throwsQuery)throw new SecurityException();return java.util.List.of(target);}
            };
            assertSame(stock,route(intent,resolver));assertNull(intent.getComponent());
        }
    }
    @Test public void excludesNativeSessionsUninstallAndNonApk() {
        for(String action:new String[]{Intent.ACTION_DELETE,Intent.ACTION_UNINSTALL_PACKAGE,Intent.ACTION_SEND,
                "android.content.pm.action.CONFIRM_INSTALL","android.content.pm.action.CONFIRM_PRE_APPROVAL"}) nativeOnly(apk().setAction(action));
        for(String extra:new String[]{"android.content.pm.extra.SESSION_ID","android.content.pm.extra.SESSION"}) nativeOnly(apk().putExtra(extra,1));
        nativeOnly(apk().setDataAndType(Uri.parse("package:example.app"),APK));
        nativeOnly(apk().setDataAndType(Uri.parse("https://example.org/a.apk"),APK));
        Intent text=apk().setType("text/plain");
        assertSame(stock,InstallerRouting.route(true,TARGET,text,"text/plain",0,caller,stock,this::unexpectedResolver));
        nativeOnly(apk().setData(null)); nativeOnly(null);
    }
    @Test public void leavesOtherExplicitComponentsAndSelectorsAlone() {
        nativeOnly(apk().setComponent(new ComponentName("other.installer","other.installer.Main")));
        nativeOnly(apk().setPackage(TARGET)); nativeOnly(apk().setPackage("other.installer"));
        nativeOnly(apk().setComponent(new ComponentName(STOCK,STOCK+".InstallStaging")));
        Intent selector=apk(); selector.setSelector(new Intent(Intent.ACTION_VIEW)); nativeOnly(selector);
    }
    @Test public void neverRedirectsShellRootInstallerFallbackOrPendingIntents() {
        for(Origin origin:new Origin[]{new Origin(0,"root",false),new Origin(2000,"com.android.shell",false),
                new Origin(1000,"android",false),
                new Origin(10123,TARGET,false),new Origin(10020,STOCK,false),new Origin(10123,null,false),
                new Origin(10123,"example.files",true),new Origin(-1,"example.files",false)}) {
            assertSame(stock,InstallerRouting.route(true,TARGET,apk(),APK,0,origin,stock,this::unexpectedResolver));
        }
    }
    @Test public void keepsUsersIsolatedAndAllowsSameUserWhenTargetResolvesThere() {
        assertSame(stock,InstallerRouting.route(true,TARGET,apk(),APK,10,caller,stock,this::unexpectedResolver));
        Origin workCaller=new Origin(1010123,"example.files",false);
        assertSame(stock,InstallerRouting.route(true,TARGET,apk(),APK,10,workCaller,stock,i->target));
        ResolveInfo workTarget=info(TARGET,COMPONENT.getClassName(),1010234);
        assertSame(workTarget,InstallerRouting.route(true,TARGET,apk(),APK,10,workCaller,stock,i->workTarget));
    }
    @Test public void requiresExactOriginalEntry() {
        for(ResolveInfo original:new ResolveInfo[]{null,new ResolveInfo(),target,
                info(STOCK,STOCK+".PackageInstallerActivity",10020)}) {
            assertSame(original,InstallerRouting.route(true,TARGET,apk(),APK,0,caller,original,this::unexpectedResolver));
        }
    }
    @Test public void missingWrongDisabledPrivateOrSuspendedTargetLeavesOriginalUnchanged() {
        ResolveInfo disabled=info(TARGET,COMPONENT.getClassName(),10234); disabled.activityInfo.enabled=false;
        ResolveInfo disabledApp=info(TARGET,COMPONENT.getClassName(),10234); disabledApp.activityInfo.applicationInfo.enabled=false;
        ResolveInfo privateActivity=info(TARGET,COMPONENT.getClassName(),10234); privateActivity.activityInfo.exported=false;
        ResolveInfo suspended=info(TARGET,COMPONENT.getClassName(),10234); suspended.activityInfo.applicationInfo.flags|=ApplicationInfo.FLAG_SUSPENDED;
        ResolveInfo permission=info(TARGET,COMPONENT.getClassName(),10234); permission.activityInfo.permission="restricted.permission";
        for(ResolveInfo result:new ResolveInfo[]{null,new ResolveInfo(),stock,disabled,disabledApp,privateActivity,suspended,permission}) {
            Intent intent=apk().setPackage(STOCK); assertSame(stock,route(intent,i->result));
            assertNull(intent.getComponent()); assertEquals(STOCK,intent.getPackage());
        }
    }
    @Test public void resolverFailureCannotPartiallyRewriteOrRetryALaunch() {
        Intent intent=apk().setPackage(STOCK); AtomicInteger calls=new AtomicInteger();
        assertSame(stock,route(intent,candidate->{calls.incrementAndGet(); candidate.setAction("changed"); throw new SecurityException();}));
        assertEquals(1,calls.get()); assertEquals(Intent.ACTION_VIEW,intent.getAction());
        assertNull(intent.getComponent()); assertEquals(STOCK,intent.getPackage());
    }
    @Test public void malformedExtrasFailClosedBeforeResolvingAnotherActivity() {
        Intent broken=new Intent(apk()) {
            @Override public boolean hasExtra(String key) { throw new android.os.BadParcelableException("test"); }
        };
        nativeOnly(broken); assertNull(broken.getComponent());
    }
    @Test public void doesNotInventUriGrantsForAnUngrantedRequest() {
        Intent intent=apk(); assertEquals(0,intent.getFlags());
        assertSame(target,route(intent,i->target)); assertEquals(0,intent.getFlags()); assertNull(intent.getClipData());
    }
}
