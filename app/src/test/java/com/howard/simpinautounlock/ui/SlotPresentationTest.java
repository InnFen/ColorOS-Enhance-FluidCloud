package com.howard.simpinautounlock.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;
import org.junit.Test;

public final class SlotPresentationTest {
    @Test
    public void unavailableEmptySlotOffersEncryptedStagingWithoutIccidOrActions() {
        SlotPresentation presentation = SlotPresentation.create(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                formalStatus(SlotState.ARMED),
                pendingStatus(PendingPinState.EMPTY));

        assertEquals("模块通信不可用，可以先加密暂存 PIN", presentation.displayText());
        assertFalse(presentation.displayText().contains("1234"));
        assertFalse(presentation.toggleEnabled());
        assertFalse(presentation.toggleState());
        assertFalse(presentation.clearEnabled());
        assertFalse(presentation.pendingWarningVisible());
    }

    @Test
    public void unavailableStagedSlotCanClearAndShowsSameSlotWarning() {
        SlotPresentation presentation = SlotPresentation.create(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                formalStatus(SlotState.ARMED),
                pendingStatus(PendingPinState.STAGED));

        assertTrue(presentation.displayText().contains("已加密保存"));
        assertTrue(presentation.displayText().contains("电话端状态待确认"));
        assertTrue(presentation.displayText().contains("首次配置时"));
        assertFalse(presentation.displayText().contains("1234"));
        assertFalse(presentation.toggleEnabled());
        assertFalse(presentation.toggleState());
        assertTrue(presentation.clearEnabled());
        assertTrue(presentation.pendingWarningVisible());
    }

    @Test
    public void unavailableClaimedSlotCanClearAndExplainsRestaging() {
        SlotPresentation presentation = SlotPresentation.create(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                formalStatus(SlotState.ARMED),
                pendingStatus(PendingPinState.CLAIMED));

        assertEquals(
                "暂存 PIN 已领取或失效；若自动解锁未生效，请重新保存",
                presentation.displayText());
        assertFalse(presentation.displayText().contains("1234"));
        assertFalse(presentation.toggleEnabled());
        assertFalse(presentation.toggleState());
        assertTrue(presentation.clearEnabled());
        assertTrue(presentation.pendingWarningVisible());
    }

    @Test
    public void availableEmptyPendingStatePreservesFormalPresentation() {
        SlotStatus formal = formalStatus(SlotState.ARMED);

        SlotPresentation presentation = SlotPresentation.create(
                SaveRoutingPolicy.Availability.AVAILABLE,
                formal,
                pendingStatus(PendingPinState.EMPTY));

        assertEquals(UiTextFormatter.status(formal), presentation.displayText());
        assertTrue(presentation.toggleEnabled());
        assertTrue(presentation.toggleState());
        assertTrue(presentation.clearEnabled());
        assertFalse(presentation.pendingWarningVisible());
    }

    @Test
    public void availablePendingStatesOverlayFormalStatusAndKeepLastFourOnly() {
        for (PendingPinState state : new PendingPinState[] {
                PendingPinState.STAGED,
                PendingPinState.CLAIMED
        }) {
            SlotPresentation presentation = SlotPresentation.create(
                    SaveRoutingPolicy.Availability.AVAILABLE,
                    formalStatus(SlotState.ARMED),
                    pendingStatus(state));

            assertTrue(state.name(), presentation.displayText().contains("状态：已启用"));
            assertTrue(state.name(), presentation.displayText().contains("•••• 1234"));
            assertFalse(state.name(), presentation.displayText().contains("8901000000000001234"));
            assertTrue(state.name(), presentation.displayText().contains(
                    state == PendingPinState.STAGED
                            ? "存在加密暂存记录，是否已生效以电话端状态为准"
                            : "暂存 PIN 已领取或失效；若自动解锁未生效，请重新保存"));
            assertTrue(state.name(), presentation.toggleEnabled());
            assertTrue(state.name(), presentation.toggleState());
            assertTrue(state.name(), presentation.clearEnabled());
            assertTrue(state.name(), presentation.pendingWarningVisible());
            assertFalse(state.name(), presentation.displayText().contains("重启"));
        }
    }

    @Test public void stagedRecordCannotOverrideConfirmedFailureOrDemandAnotherRestart() {
        SlotStatus failure=new SlotStatus(0,SlotState.NEEDS_CONFIRMATION,"1234","1234",true,SlotRecord.RESULT_FAILURE,2,42);
        SlotPresentation presentation=SlotPresentation.create(SaveRoutingPolicy.Availability.AVAILABLE,failure,pendingStatus(PendingPinState.STAGED));
        assertTrue(presentation.displayText().contains("需要重新保存 PIN"));
        assertTrue(presentation.displayText().contains("最近结果：失败"));
        assertTrue(presentation.displayText().contains("存在加密暂存记录，是否已生效以电话端状态为准"));
        assertFalse(presentation.displayText().contains("重启"));
        assertFalse(presentation.toggleState()); assertFalse(presentation.toggleEnabled());
        assertTrue(presentation.clearEnabled()); assertTrue(presentation.pendingWarningVisible());
    }

    private static SlotStatus formalStatus(SlotState state) {
        return new SlotStatus(
                0,
                state,
                "1234",
                "1234",
                true,
                SlotRecord.RESULT_SUCCESS,
                3,
                42);
    }

    private static PendingPinStatus pendingStatus(PendingPinState state) {
        return new PendingPinStatus(0, state);
    }
}
