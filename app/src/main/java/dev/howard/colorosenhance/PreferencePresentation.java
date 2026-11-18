package dev.howard.colorosenhance;

/** Whether displayed preferences are acknowledged current values or only the last known values. */
record PreferencePresentation(State state, boolean lastAcknowledgedEnabled) {
    enum State { DISCONNECTED, CONFIRMED, UNCONFIRMED }

    static PreferencePresentation from(PreferenceSession session) {
        return new PreferencePresentation(session == null ? State.DISCONNECTED
                : session.available() ? State.CONFIRMED : State.UNCONFIRMED,
                session != null && session.flag("enabled", false));
    }

    boolean canWrite() { return state == State.CONFIRMED; }

    String status() {
        String previous = lastAcknowledgedEnabled ? "已启用" : "已关闭";
        return switch (state) {
            case DISCONNECTED -> "等待配置服务";
            case CONFIRMED -> previous;
            case UNCONFIRMED -> "状态未确认（上次确认：" + previous + "）";
        };
    }

    String detail() {
        return switch (state) {
            case DISCONNECTED -> "配置服务未连接，当前不能保存开关。";
            case CONFIRMED -> "关闭后保留本组设置并恢复系统行为。";
            case UNCONFIRMED -> "当前状态未确认。下方仅显示上次确认的设置，已暂停保存；请重新连接配置服务。";
        };
    }

    String saveFailure() {
        return switch (state) {
            case DISCONNECTED -> "保存失败，配置服务未连接；请重新连接后确认设置。";
            case CONFIRMED -> "保存失败，已恢复上次设置；请检查配置服务连接后重试。";
            case UNCONFIRMED -> "保存和恢复均未获确认，当前状态未确认；已暂停保存，请重新连接配置服务。";
        };
    }
}
