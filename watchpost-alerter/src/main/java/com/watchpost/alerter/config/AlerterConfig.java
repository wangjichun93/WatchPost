package com.watchpost.alerter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 告警模块装配。
 * @EnableScheduling 驱动 DeliveryRetryScheduler (邮件失败重试 1/5/15min) 与历史告警清理。
 * 规则引擎/收敛/通知路由/判定服务均由组件扫描装配。
 */
@Configuration
@EnableScheduling
public class AlerterConfig {
}
