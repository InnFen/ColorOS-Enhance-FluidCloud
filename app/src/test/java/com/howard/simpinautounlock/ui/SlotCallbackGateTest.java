package com.howard.simpinautounlock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class SlotCallbackGateTest {
    @Test
    public void callbackOwnedByReplacedActivityCannotMutate() {
        SlotCallbackGate gate = new SlotCallbackGate();
        SlotCallbackGate.Owner oldActivity = gate.replaceOwner();
        SlotCallbackGate.ActionToken oldSave = oldActivity.beginAction(0);
        AtomicInteger mutations = new AtomicInteger();

        gate.replaceOwner();

        assertFalse(oldActivity.runIfCurrent(oldSave, mutations::incrementAndGet));
        assertEquals(0, mutations.get());
    }

    @Test
    public void callbackOwnedByDestroyedActivityCannotMutate() {
        SlotCallbackGate gate = new SlotCallbackGate();
        SlotCallbackGate.Owner activity = gate.replaceOwner();
        SlotCallbackGate.ActionToken oldClear = activity.beginAction(0);
        AtomicInteger mutations = new AtomicInteger();

        activity.close();

        assertFalse(activity.runIfCurrent(oldClear, mutations::incrementAndGet));
        assertEquals(0, mutations.get());
    }

    @Test
    public void destroyingReplacedActivityDoesNotInvalidateReplacement() {
        SlotCallbackGate gate = new SlotCallbackGate();
        SlotCallbackGate.Owner oldActivity = gate.replaceOwner();
        SlotCallbackGate.Owner replacement = gate.replaceOwner();
        SlotCallbackGate.ActionToken replacementSave = replacement.beginAction(1);
        AtomicInteger mutations = new AtomicInteger();

        oldActivity.close();

        assertTrue(replacement.runIfCurrent(replacementSave, mutations::incrementAndGet));
        assertEquals(1, mutations.get());
    }

    @Test
    public void refreshingBothSlotsNeverInvalidatesEitherInflightAction() {
        for (int actionSlot = 0; actionSlot < 2; actionSlot++) {
            SlotCallbackGate gate = new SlotCallbackGate();
            SlotCallbackGate.Owner activity = gate.replaceOwner();
            SlotCallbackGate.ActionToken action = activity.beginAction(actionSlot);
            AtomicInteger mutations = new AtomicInteger();

            activity.beginQuery(0);
            activity.beginQuery(1);

            assertTrue(
                    Integer.toString(actionSlot),
                    activity.runIfCurrent(action, mutations::incrementAndGet));
            assertEquals(Integer.toString(actionSlot), 1, mutations.get());
        }
    }

    @Test
    public void newerSameSlotActionRejectsOlderActionCallback() {
        SlotCallbackGate gate = new SlotCallbackGate();
        SlotCallbackGate.Owner activity = gate.replaceOwner();
        SlotCallbackGate.ActionToken older = activity.beginAction(0);
        SlotCallbackGate.ActionToken newer = activity.beginAction(0);
        AtomicInteger mutations = new AtomicInteger();

        assertFalse(activity.runIfCurrent(older, mutations::incrementAndGet));
        assertTrue(activity.runIfCurrent(newer, mutations::incrementAndGet));
        assertEquals(1, mutations.get());
    }

    @Test
    public void newerSameSlotQueryRejectsOlderWithoutChangingOtherSlot() {
        SlotCallbackGate gate = new SlotCallbackGate();
        SlotCallbackGate.Owner activity = gate.replaceOwner();
        SlotCallbackGate.QueryToken olderSlotZero = activity.beginQuery(0);
        SlotCallbackGate.QueryToken slotOne = activity.beginQuery(1);
        SlotCallbackGate.QueryToken newerSlotZero = activity.beginQuery(0);
        AtomicInteger mutations = new AtomicInteger();

        assertFalse(activity.runIfCurrent(olderSlotZero, mutations::incrementAndGet));
        assertTrue(activity.runIfCurrent(newerSlotZero, mutations::incrementAndGet));
        assertTrue(activity.runIfCurrent(slotOne, mutations::incrementAndGet));
        assertEquals(2, mutations.get());
    }

    @Test
    public void laterSameSlotActionRejectsOutstandingQueryCallback() {
        SlotCallbackGate gate = new SlotCallbackGate();
        SlotCallbackGate.Owner activity = gate.replaceOwner();
        SlotCallbackGate.QueryToken query = activity.beginQuery(1);
        AtomicInteger mutations = new AtomicInteger();

        activity.beginAction(1);

        assertFalse(activity.runIfCurrent(query, mutations::incrementAndGet));
        assertEquals(0, mutations.get());
    }
}
