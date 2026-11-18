package dev.howard.colorosenhance.updates;

import static org.junit.Assert.*;
import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=36,application=Application.class)
public class UpdateAppLifecycleTest {
    @Test public void rotationAndNavigationDoNotResetChecksButReturningFromBackgroundDoes() {
        var f=new LaunchUpdateCheckTest.Fixture();
        var lifecycle=new UpdateAppLifecycle(new Handler(Looper.getMainLooper()),f.checks);
        lifecycle.onActivityStarted(null);f.checks.check();f.complete();
        lifecycle.onActivityStarted(null); // A settings child or the manual update Activity.
        lifecycle.onActivityStopped(null);f.checks.check();assertEquals(1,f.requests);
        lifecycle.onActivityStopped(null); // Rotation: stop/start before the short transition grace ends.
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        lifecycle.onActivityStarted(null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        f.checks.check();assertEquals(1,f.requests);assertTrue(f.worker.isEmpty());
        lifecycle.onActivityStopped(null);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertNull(f.checks.pending());
        lifecycle.onActivityStarted(null);f.checks.check();f.complete();assertEquals(2,f.requests);
    }
    @Test public void openingOnlyAManualUpdateOrMigrationPageDoesNotLaunchAnAutomaticRequest() {
        var f=new LaunchUpdateCheckTest.Fixture();
        var lifecycle=new UpdateAppLifecycle(new Handler(Looper.getMainLooper()),f.checks);
        lifecycle.onActivityStarted(null);assertTrue(f.worker.isEmpty());
        lifecycle.onActivityStopped(null);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(0,f.requests);assertTrue(f.worker.isEmpty());
    }
}
