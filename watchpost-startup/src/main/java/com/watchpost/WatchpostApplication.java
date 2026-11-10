package com.watchpost;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.core.env.Environment;

/**
 * 望哨 WATCHPOST 启动类 (v1 单体装配: manager + collector + alerter 同进程)。
 *
 * 组件扫描根包 com.watchpost, 覆盖全部模块:
 *   - collector: 协议采集器 + 调度 (CollectScheduler 在 ApplicationReadyEvent 启动)
 *   - warehouse: JPA 实体/仓库 + 两级存储 (Redis 实时 + TSDB 历史)
 *   - alerter:   规则引擎 + 收敛状态机 + 通知 (订阅 MetricQueue)
 *   - manager:   REST API + Security + WebSocket + 审计
 *
 * 集群演进: collector 可单独打包为采集器节点 (watchpost-collector 独立部署), 此进程退化为纯 manager。
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.watchpost")
@ConfigurationPropertiesScan(basePackages = "com.watchpost")
public class WatchpostApplication {

    public static void main(String[] args) {
        Environment env = SpringApplication.run(WatchpostApplication.class, args).getEnvironment();
        String port = env.getProperty("server.port", "1157");
        log.info("""

                ----------------------------------------------------------
                  望哨 WATCHPOST 无 Agent 实时监控系统 已启动
                  本地访问:      http://localhost:{}
                  API 文档:      http://localhost:{}/swagger-ui.html
                  健康检查:      http://localhost:{}/actuator/health
                  实时推送:      ws://localhost:{}/ws/realtime
                  默认管理员:    {} (口令见配置, 请首登改密)
                ----------------------------------------------------------""",
                port, port, port, port, env.getProperty("watchpost.bootstrap.admin-username", "admin"));
    }
}
