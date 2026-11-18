package com.howard.simpinautounlock.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;
import org.junit.Test;

public final class UiTextFormatterTest {
    @Test
    public void formatsMatchedArmedSlotWithoutSensitiveData() {
        SlotStatus status = new SlotStatus(
                0,
                SlotState.ARMED,
                "1234",
                "1234",
                true,
                SlotRecord.RESULT_SUCCESS,
                3,
                42);

        String text = UiTextFormatter.status(status);

        assertTrue(text.contains("•••• 1234"));
        assertTrue(text.contains("匹配"));
        assertFalse(text.contains("8901000000000001234"));
    }

    @Test
    public void formatsMismatchWithBothSuffixes() {
        SlotStatus status = new SlotStatus(
                1,
                SlotState.SIM_MISMATCH,
                "1234",
                "5678",
                false,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                42);

        String text = UiTextFormatter.status(status);

        assertTrue(text.contains("•••• 1234"));
        assertTrue(text.contains("•••• 5678"));
        assertTrue(text.contains("不匹配"));
    }

    @Test
    public void formatsDisabledFailureAndRemainingAttempts() {
        SlotStatus status = new SlotStatus(
                0,
                SlotState.DISABLED,
                "1234",
                "1234",
                true,
                SlotRecord.RESULT_FAILURE,
                2,
                42);

        String text = UiTextFormatter.status(status);

        assertTrue(text.contains("已停用"));
        assertTrue(text.contains("失败"));
        assertTrue(text.contains("剩余尝试次数：2"));
    }

    @Test
    public void formatsEmptySlotWithoutPretendingItMatches() {
        SlotStatus status = new SlotStatus(
                1,
                SlotState.EMPTY,
                "",
                "5678",
                false,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                -1);

        String text = UiTextFormatter.status(status);

        assertTrue(text.contains("未配置"));
        assertTrue(text.contains("当前 SIM：•••• 5678"));
        assertFalse(text.contains("SIM：匹配"));
    }

    @Test
    public void refusesFullOrMalformedIccidValues() {
        SlotStatus status = new SlotStatus(
                0,
                SlotState.SIM_MISMATCH,
                "8901000000000001234",
                "12A4",
                false,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                42);

        String text = UiTextFormatter.status(status);

        assertFalse(text.contains("8901000000000001234"));
        assertFalse(text.contains("12A4"));
    }

    @Test
    public void missingStatusIsSafe() {
        String text = UiTextFormatter.status(null);

        assertTrue(text.contains("状态：不可用"));
    }

    @Test
    public void formatsPendingAndConfirmationStatesClearly() {
        SlotStatus pending = new SlotStatus(
                0, SlotState.PENDING_RESULT, "1234", "1234", true,
                SlotRecord.RESULT_UNKNOWN, -1, 42);
        SlotStatus confirmation = new SlotStatus(
                0, SlotState.NEEDS_CONFIRMATION, "1234", "1234", true,
                SlotRecord.RESULT_FAILURE, 2, 42);

        assertTrue(UiTextFormatter.status(pending).contains("等待提交结果"));
        assertTrue(UiTextFormatter.status(confirmation).contains("需要重新保存 PIN"));
    }

    @Test
    public void pendingOnlyFormattingUsesExactCopyAndNeverLeaksFormalIccid() {
        SlotStatus formal = new SlotStatus(
                0,
                SlotState.ARMED,
                "1234",
                "5678",
                false,
                SlotRecord.RESULT_UNKNOWN,
                -1,
                42);

        assertPendingOnlyText(formal, PendingPinState.EMPTY,
                "模块通信不可用，可以先加密暂存 PIN");
        assertPendingOnlyText(formal, PendingPinState.CLAIMED,
                "暂存 PIN 已领取或失效；若自动解锁未生效，请重新保存");
    }

    @Test
    public void reachableFormattingKeepsFormalTextAndAddsNonemptyPendingOverlay() {
        SlotStatus formal = new SlotStatus(
                0,
                SlotState.ARMED,
                "1234",
                "1234",
                true,
                SlotRecord.RESULT_SUCCESS,
                3,
                42);

        String empty = UiTextFormatter.mergedStatus(
                SaveRoutingPolicy.Availability.AVAILABLE,
                formal,
                new PendingPinStatus(0, PendingPinState.EMPTY));
        String staged = UiTextFormatter.mergedStatus(
                SaveRoutingPolicy.Availability.AVAILABLE,
                formal,
                new PendingPinStatus(0, PendingPinState.STAGED));

        assertEquals(UiTextFormatter.status(formal), empty);
        assertTrue(staged.startsWith(UiTextFormatter.status(formal)));
        assertTrue(staged.contains("存在加密暂存记录，是否已生效以电话端状态为准"));
        assertFalse(staged.contains("重启"));
        assertTrue(staged.contains("•••• 1234"));
    }

    @Test public void unavailableStagedStatusProvidesConditionalFirstSetupGuidance() {
        String text=UiTextFormatter.mergedStatus(SaveRoutingPolicy.Availability.UNAVAILABLE,null,new PendingPinStatus(0,PendingPinState.STAGED));
        assertTrue(text.contains("已加密保存")); assertTrue(text.contains("电话端状态待确认"));
        assertTrue(text.contains("首次配置时")); assertTrue(text.contains("模块已启用")); assertTrue(text.contains("已勾选电话作用域"));
        assertTrue(text.contains("总开关开启")); assertTrue(text.contains("配置同步完成"));
        assertTrue(text.contains("通常只需完整重启一次")); assertTrue(text.contains("结果以开机后状态为准"));
        assertFalse(text.contains("成功后开关显示已启用")); assertFalse(text.contains("••••"));
    }

    @Test public void failedFormalStatusWithStagedRecordDoesNotSuggestAnotherRestart() {
        SlotStatus failed=new SlotStatus(0,SlotState.NEEDS_CONFIRMATION,"1234","1234",true,SlotRecord.RESULT_FAILURE,2,42);
        String text=UiTextFormatter.mergedStatus(SaveRoutingPolicy.Availability.AVAILABLE,failed,new PendingPinStatus(0,PendingPinState.STAGED));
        assertTrue(text.contains("状态：需要重新保存 PIN")); assertTrue(text.contains("最近结果：失败"));
        assertTrue(text.contains("存在加密暂存记录，是否已生效以电话端状态为准")); assertFalse(text.contains("重启"));
    }

    private static void assertPendingOnlyText(
            SlotStatus formal,
            PendingPinState pendingState,
            String expected) {
        String text = UiTextFormatter.mergedStatus(
                SaveRoutingPolicy.Availability.UNAVAILABLE,
                formal,
                new PendingPinStatus(0, pendingState));

        assertEquals(expected, text);
        assertFalse(text.contains("1234"));
        assertFalse(text.contains("5678"));
        assertFalse(text.contains("••••"));
    }
}
