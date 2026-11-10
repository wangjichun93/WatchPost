package com.watchpost.common.enums;

/** 规则生效时段 (PRD 第04章)。 */
public enum EffectivePeriod {
    ALL_DAY,   // 全天
    WORK,      // 工作时段 (周一至周五 09:00-18:00, 可在配置调整)
    CRON       // 自定义 cron 表达式
}
