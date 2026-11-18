package com.howard.simpinautounlock.management;

import static org.junit.Assert.*;

import android.content.Intent;
import android.os.Bundle;
import com.howard.simpinautounlock.MainActivity;
import com.howard.simpinautounlock.ModuleApplication;
import com.howard.simpinautounlock.ipc.ConfigContract;
import com.howard.simpinautounlock.ipc.PhoneBridgeClient;
import com.howard.simpinautounlock.pending.PendingPinState;
import dev.howard.colorosenhance.R;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, application = SimManagementActivityTest.UiApplication.class)
public class SimManagementActivityTest {
    @Test public void legacyEntryOnlyRedirectsToTheUnifiedSlotPage() {
        UiApplication application = (UiApplication) RuntimeEnvironment.getApplication();
        try (var host = Robolectric.buildActivity(MainActivity.class).create()) {
            MainActivity activity = host.get();
            Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(next);
            assertEquals(dev.howard.colorosenhance.MainActivity.class.getName(), next.getComponent().getClassName());
            assertEquals("sim_slots", next.getStringExtra("group"));
            assertEquals(Set.of("group"), next.getExtras().keySet());
            assertTrue(activity.isFinishing());
            assertNull(activity.findViewById(R.id.sim_slot_0));
            assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
        assertEquals(0, application.bridge.actions);
        assertTrue(application.bridge.queries.isEmpty());
        assertEquals(0, application.repository.writes);
    }

    @Test public void restoredLegacyEntryDoesNotForwardOldInputOrExecuteAnOperation() {
        UiApplication application = (UiApplication) RuntimeEnvironment.getApplication();
        Intent incoming = new Intent(application, MainActivity.class)
                .putExtra("pin", "739251").putExtra("confirmation", "739251");
        Bundle restored = new Bundle();
        restored.putString("pin", "648203");
        try (var host = Robolectric.buildActivity(MainActivity.class, incoming).create(restored)) {
            Intent next = Shadows.shadowOf(host.get()).getNextStartedActivity();
            assertNotNull(next);
            assertEquals("sim_slots", next.getStringExtra("group"));
            assertEquals(Set.of("group"), next.getExtras().keySet());
            assertFalse(next.hasExtra("pin"));
            assertFalse(next.hasExtra("confirmation"));
            assertTrue(host.get().isFinishing());
        }
        assertEquals(0, application.bridge.actions);
        assertTrue(application.bridge.queries.isEmpty());
        assertEquals(0, application.repository.writes);
    }

    @Test public void applicationOwnedSaveSurvivesLegacyActivityDestructionAndRecreation() {
        UiApplication application = (UiApplication) RuntimeEnvironment.getApplication();
        SimManagementController controller = application.simManagement();
        controller.save(0, "739251", "739251");
        assertNotNull(application.bridge.save);
        assertTrue(controller.snapshot().slots().getFirst().busy());
        assertEquals(1, application.bridge.actions);

        try (var first = Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
            assertTrue(first.get().isFinishing());
            assertSame(controller, application.simManagement());
        }
        application.bridge.save.onResult(new PhoneBridgeClient.Result(ConfigContract.RESULT_MODULE_UNAVAILABLE, null));
        assertEquals(PendingPinState.STAGED, application.repository.status(0).state());
        assertFalse(controller.snapshot().slots().getFirst().busy());

        try (var second = Robolectric.buildActivity(MainActivity.class).create().start().resume()) {
            assertTrue(second.get().isFinishing());
            assertSame(controller, application.simManagement());
            assertEquals("sim_slots", Shadows.shadowOf(second.get()).getNextStartedActivity().getStringExtra("group"));
        }
        assertEquals(PendingPinState.STAGED, application.repository.status(0).state());
        assertEquals(1, application.repository.writes);
        assertEquals(1, application.bridge.actions);
        assertTrue(application.bridge.queries.isEmpty());
    }

    public static class UiApplication extends ModuleApplication {
        final SimManagementControllerTest.MemoryPending repository = new SimManagementControllerTest.MemoryPending();
        final SimManagementControllerTest.DeferredBridge bridge = new SimManagementControllerTest.DeferredBridge();
        SimManagementController controller;
        @Override public void onCreate() { controller = new SimManagementController(this, repository, bridge); }
        @Override public synchronized SimManagementController simManagement() { return controller; }
    }
}
