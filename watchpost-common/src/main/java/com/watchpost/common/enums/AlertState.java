package com.watchpost.common.enums;

/** 告警收敛状态机 (PRD 第04章): 正常→待判定→已触发→通知中→已确认→已恢复→关闭。 */
public enum AlertState {
    NORMAL, PENDING, TRIGGERED, NOTIFYING, ACKNOWLEDGED, RECOVERED, CLOSED;

    public boolean isActive() {
        return this == PENDING || this == TRIGGERED || this == NOTIFYING || this == ACKNOWLEDGED;
    }
    public boolean isTerminal() {
        return this == CLOSED;
    }
}
