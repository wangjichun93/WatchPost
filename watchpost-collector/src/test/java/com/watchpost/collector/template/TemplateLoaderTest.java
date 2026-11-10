package com.watchpost.collector.template;

import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.CollectTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 采集模板加载测试: 验证 5 个内置 YAML 模板 (制品#2) 能正确反序列化为 CollectTemplate。
 * 这是 M1 关键路径 — 模板解析错误会导致采集器拿不到命令/SQL。
 */
class TemplateLoaderTest {

    private TemplateLoader loader;

    @BeforeEach
    void setUp() {
        loader = new TemplateLoader();
        loader.reload();
    }

    @Test
    @DisplayName("五个内置模板全部加载 (linux/http/mysql/postgresql/oracle)")
    void loadsAllFiveTemplates() {
        var all = loader.all();
        assertEquals(5, all.size(), "应加载5个内置模板, 实际: " + all.keySet());
        assertTrue(all.containsKey("linux"));
        assertTrue(all.containsKey("http"));
        assertTrue(all.containsKey("mysql"));
        assertTrue(all.containsKey("postgresql"));
        assertTrue(all.containsKey("oracle"));
    }

    @Test
    @DisplayName("Linux SSH 模板: 协议/命令/字段映射正确")
    void linuxTemplate() {
        CollectTemplate t = loader.get("linux");
        assertEquals(AppType.linux, t.getApp());
        assertEquals(ProtocolType.ssh, t.getProtocol());
        assertEquals(5, t.getTimeout(), "PRD: 采集超时 ≤5s");
        assertNotNull(t.getMetrics());
        assertFalse(t.getMetrics().isEmpty());
        // cpu 组含 usage_percent 字段, 带换算表达式
        var cpu = t.getMetrics().stream().filter(g -> g.getGroup().equals("cpu")).findFirst().orElseThrow();
        assertEquals("vmstat 1 2 | tail -1", cpu.getCommand());
        assertTrue(cpu.getFields().stream().anyMatch(f -> f.getName().equals("usage_percent")));
        // disk 组按设备打标签 (table 解析)
        var disk = t.getMetrics().stream().filter(g -> g.getGroup().equals("disk")).findFirst().orElseThrow();
        assertEquals("table", disk.getParser());
        assertEquals("device", disk.getKeyBy());
    }

    @Test
    @DisplayName("HTTP 模板: 请求/断言/分段耗时配置正确")
    void httpTemplate() {
        CollectTemplate t = loader.get("http");
        assertEquals(ProtocolType.http, t.getProtocol());
        assertNotNull(t.getRequest());
        assertEquals("GET", t.getRequest().getMethod());
        assertNotNull(t.getChecks());
        assertTrue(t.getChecks().isSslCertDays());
        assertNotNull(t.getChecks().getJsonPathAssert());
        assertFalse(t.getChecks().getJsonPathAssert().isEmpty());
        assertNotNull(t.getTimingPhases());
        assertTrue(t.getTimingPhases().containsAll(java.util.List.of("dns", "connect", "ttfb")));
    }

    @Test
    @DisplayName("三类数据库模板: JDBC 协议 + 只读查询 + 列映射")
    void jdbcTemplates() {
        for (String app : new String[]{"mysql", "postgresql", "oracle"}) {
            CollectTemplate t = loader.get(app);
            assertEquals(ProtocolType.jdbc, t.getProtocol(), app + " 应为 JDBC 协议");
            assertNotNull(t.getQueries(), app + " 应含 queries");
            assertFalse(t.getQueries().isEmpty(), app + " queries 不应为空");
            // 每个查询应有 group + sql
            t.getQueries().forEach(q -> {
                assertNotNull(q.getGroup(), app + " query 缺 group");
                assertNotNull(q.getSql(), app + " query 缺 sql");
                assertFalse(q.getSql().isBlank(), app + " query sql 为空");
            });
        }
        // MySQL 含主从延迟查询 (PRD 关键指标)
        CollectTemplate mysql = loader.get("mysql");
        assertTrue(mysql.getQueries().stream()
                .anyMatch(q -> q.getSql().toUpperCase().contains("SLAVE STATUS")),
                "MySQL 应含 SHOW SLAVE STATUS (主从延迟)");
        // Oracle 含表空间查询
        CollectTemplate oracle = loader.get("oracle");
        assertTrue(oracle.getQueries().stream()
                .anyMatch(q -> q.getGroup().equals("tablespace")),
                "Oracle 应含表空间使用率查询");
    }

    @Test
    @DisplayName("未知类型抛出 TEMPLATE_INVALID")
    void unknownTypeThrows() {
        assertThrows(com.watchpost.common.exception.BizException.class, () -> loader.get("windows"));
    }
}
