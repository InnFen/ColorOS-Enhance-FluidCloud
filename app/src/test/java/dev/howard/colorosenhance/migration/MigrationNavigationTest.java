package dev.howard.colorosenhance.migration;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import dev.howard.colorosenhance.EnhanceApplication;
import dev.howard.colorosenhance.MainActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=36,application=EnhanceApplication.class)
public class MigrationNavigationTest {
    private void installLegacy(){
        var info=new PackageInfo();info.packageName=MigrationProtocol.LEGACY;
        info.applicationInfo=new ApplicationInfo();info.applicationInfo.packageName=info.packageName;
        shadowOf(RuntimeEnvironment.getApplication().getPackageManager()).installPackage(info);
    }
    @Test public void returningToTheMainActivityReopensAnUnfinishedMigrationWithoutDuplicateLaunches(){
        installLegacy();
        try(var home=Robolectric.buildActivity(MainActivity.class).create().start()){
            var app=(EnhanceApplication)home.get().getApplication();var migration=app.migration();
            assertEquals(MigrationActivity.class.getName(),shadowOf(home.get()).getNextStartedActivity().getComponent().getClassName());
            migration.foreground(home.get());assertNull(shadowOf(home.get()).getNextStartedActivity());
            try(var page=Robolectric.buildActivity(MigrationActivity.class).create().start()){
                migration.background(page.get());migration.foreground(home.get());
                assertEquals(MigrationActivity.class.getName(),shadowOf(home.get()).getNextStartedActivity().getComponent().getClassName());
                assertNull(shadowOf(home.get()).getNextStartedActivity());
            }
        }
    }
    @Test public void backFromAnUnfinishedMigrationFinishesTheTaskAndPreservesPendingState(){
        installLegacy();
        try(var page=Robolectric.buildActivity(MigrationActivity.class).create().start().resume()){
            var activity=page.get();var app=(EnhanceApplication)activity.getApplication();
            assertTrue(app.migration().blocksEditing());
            activity.getOnBackPressedDispatcher().onBackPressed();
            assertTrue(activity.isFinishing());assertTrue(app.migration().blocksEditing());
        }
    }
}
