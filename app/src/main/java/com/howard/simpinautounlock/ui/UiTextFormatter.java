package com.howard.simpinautounlock.ui;

import com.howard.simpinautounlock.core.SlotState;
import com.howard.simpinautounlock.core.SlotRecord;
import com.howard.simpinautounlock.core.SlotStatus;
import com.howard.simpinautounlock.pending.PendingPinState;
import com.howard.simpinautounlock.pending.PendingPinStatus;

public final class UiTextFormatter {
    private static final String MASK = "•••• ";

    private UiTextFormatter() {}

    public static String status(SlotStatus status) {
        if (status == null || status.state() == null) {
            return "状态：不可用";
        }
        StringBuilder text = new StringBuilder();
        text.append("状态：").append(switch (status.state()) {
            case EMPTY -> "未配置";
            case ARMED -> "已启用";
            case DISABLED -> "已停用";
            case PENDING_RESULT -> "等待提交结果";
            case NEEDS_CONFIRMATION -> "需要重新保存 PIN";
            case SIM_MISMATCH -> "SIM 不匹配";
        });

        String bound = maskedSuffix(status.boundIccidSuffix());
        String current = maskedSuffix(status.currentIccidSuffix());
        if (!bound.isEmpty()) {
            text.append("\n已绑定 SIM：").append(bound);
        }
        if ((status.state() == SlotState.SIM_MISMATCH
                || status.state() == SlotState.EMPTY) && !current.isEmpty()) {
            text.append("\n当前 SIM：").append(current);
        }

        if (status.state() == SlotState.SIM_MISMATCH) {
            text.append("\nSIM：不匹配");
        } else if (status.state() != SlotState.EMPTY) {
            text.append(status.simMatches() ? "\nSIM：匹配" : "\nSIM：当前不可用");
        }

        if (status.lastResultCode() == SlotRecord.RESULT_SUCCESS) {
            text.append("\n最近结果：成功");
        } else if (status.lastResultCode() == SlotRecord.RESULT_FAILURE) {
            text.append("\n最近结果：失败");
        } else {
            text.append("\n最近结果：未知");
        }
        if (status.remainingAttempts() >= 0) {
            text.append("\n剩余尝试次数：").append(status.remainingAttempts());
        }
        return text.toString();
    }

    public static String mergedStatus(
            SaveRoutingPolicy.Availability availability,
            SlotStatus phoneStatus,
            PendingPinStatus pendingStatus) {
        PendingPinState pendingState = pendingState(pendingStatus);
        if (availability != SaveRoutingPolicy.Availability.AVAILABLE) {
            return pendingText(pendingState);
        }

        String formalText = status(phoneStatus);
        if (pendingState == PendingPinState.EMPTY) {
            return formalText;
        }
        String overlay = pendingState == PendingPinState.STAGED
                ? "存在加密暂存记录，是否已生效以电话端状态为准。"
                : pendingText(pendingState);
        return formalText + "\n" + overlay;
    }

    private static String pendingText(PendingPinState state) {
        return switch (state) {
            case EMPTY -> "模块通信不可用，可以先加密暂存 PIN";
            case STAGED -> "PIN 已加密保存，电话端状态待确认。\n首次配置时，请确认模块已启用、已勾选电话作用域、总开关开启且配置同步完成。通常只需完整重启一次，模块会在同次开机导入并尝试解锁；结果以开机后状态为准。";
            case CLAIMED -> "暂存 PIN 已领取或失效；若自动解锁未生效，请重新保存";
        };
    }

    private static PendingPinState pendingState(PendingPinStatus pendingStatus) {
        return pendingStatus == null || pendingStatus.state() == null
                ? PendingPinState.EMPTY
                : pendingStatus.state();
    }

    private static String maskedSuffix(String suffix) {
        if (suffix == null || suffix.length() != 4) {
            return "";
        }
        for (int index = 0; index < suffix.length(); index++) {
            char value = suffix.charAt(index);
            if (value < '0' || value > '9') {
                return "";
            }
        }
        return MASK + suffix;
    }
}
