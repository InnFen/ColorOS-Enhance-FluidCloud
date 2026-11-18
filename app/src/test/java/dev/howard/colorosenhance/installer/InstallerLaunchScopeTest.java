package dev.howard.colorosenhance.installer;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,manifest=Config.NONE)
public class InstallerLaunchScopeTest {
    final InstallerLaunchScope scope=new InstallerLaunchScope();
    final Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example/apk"),InstallerRouting.APK)
            .setClassName("custom.installer","custom.installer.Install");
    final InstallerRouting.Origin origin=new InstallerRouting.Origin(10123,"example.caller",false);
    ActivityInfo info() { ActivityInfo a=new ActivityInfo();a.packageName="custom.installer";a.name="custom.installer.Install";
        a.applicationInfo=new ApplicationInfo();a.applicationInfo.uid=10234;a.applicationInfo.packageName=a.packageName;return a; }
    boolean skip(Intent i) {return scope.matches(i,info(),10123,"example.caller",0,"custom.installer");}
    @Test public void onlyMatchesActuallyRoutedRequestWithinItsLaunch() throws Throwable {
        scope.mark(intent,origin,0); assertFalse(skip(intent));
        scope.within(()->{ assertFalse(skip(intent));scope.mark(intent,origin,0);assertTrue(skip(intent));
            assertFalse(skip(new Intent(intent)));return null; });
        assertFalse(skip(intent));
    }
    @Test public void rejectsOtherTargetsCallersUsersAndMutatedPayload() throws Throwable {
        scope.within(()->{scope.mark(intent,origin,0);
            assertFalse(scope.matches(intent,info(),10124,"example.caller",0,"custom.installer"));
            assertFalse(scope.matches(intent,info(),10123,"other.caller",0,"custom.installer"));
            assertFalse(scope.matches(intent,info(),10123,"example.caller",10,"custom.installer"));
            assertFalse(scope.matches(intent,info(),10123,"example.caller",0,"other.installer"));
            intent.setAction(Intent.ACTION_SEND);assertFalse(skip(intent));return null;});
    }
    @Test public void nestedLaunchCannotReuseOuterApprovalAndRestoresOnFailure() throws Throwable {
        scope.within(()->{scope.mark(intent,origin,0);
            try{scope.within(()->{assertFalse(skip(intent));throw new IllegalStateException();});}
            catch(IllegalStateException expected){} assertTrue(skip(intent));return null;});
        assertFalse(skip(intent));
    }
    @Test public void onlyOrdinaryAppStartPromptsAreEligible() {
        for(int type:new int[]{0,5,6})assertTrue(InstallerLaunchScope.ordinaryConfirmation(type));
        for(int type:new int[]{-1,1,2,3,4,7,8,9,10})assertFalse(InstallerLaunchScope.ordinaryConfirmation(type));
    }
}
