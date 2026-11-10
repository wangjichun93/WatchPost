package com.watchpost.collector.parser;

import com.watchpost.common.enums.FieldType;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.spi.RuleExpression;
import com.watchpost.common.expression.JexlRuleExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命令输出解析测试: 用真实 Linux 命令输出验证 line/awk/table 解析与单位换算。
 * 这是 SSH 采集的核心 — 解析错误会导致指标值错误进而误告警。
 */
class CommandParserTest {

    private final CommandParser parser = new CommandParser();
    private final RuleExpression expr = new JexlRuleExpression();
    private final CommandParser.ExprEvaluator eval = (e, b) -> expr.eval(e, b);

    private CollectTemplate.FieldDef field(String name, String type, String unit, String ex, Integer col) {
        return CollectTemplate.FieldDef.builder()
                .name(name).type(type).unit(unit).expr(ex).column(col).build();
    }

    @Test
    @DisplayName("awk 解析: vmstat 末行, CPU 使用率 = 100 - idle(c15)")
    void parseVmstatCpu() {
        // vmstat 1 2 | tail -1 的真实输出 (17列): r b swpd free buff cache si so bi bo in cs us sy id wa st
        String output = " 0  0 123456 789012 345678 901234  0  0  12  34 567 890  5  3 90  2  0";
        CollectTemplate.MetricGroup g = CollectTemplate.MetricGroup.builder()
                .group("cpu").parser("awk")
                .fields(List.of(field("usage_percent", "double", "%", "100 - c15", null)))
                .build();
        List<MetricValue> values = parser.parse(g, output, eval);
        assertEquals(1, values.size());
        MetricValue v = values.get(0);
        assertEquals("usage_percent", v.getFieldName());
        assertEquals(FieldType.DOUBLE, v.getFieldType());
        // idle=90 (c15) -> usage=10
        assertEquals(10.0, v.getDoubleValue(), 0.001);
    }

    @Test
    @DisplayName("awk 解析: free -m Mem 行, 内存使用率 = (total-available)/total*100")
    void parseFreeMemory() {
        // free -m | grep Mem 输出: Mem: total used free shared buff/cache available
        String output = "Mem:          15884        6234        3456         512        6194        8890";
        CollectTemplate.MetricGroup g = CollectTemplate.MetricGroup.builder()
                .group("memory").parser("awk")
                .fields(List.of(field("usage_percent", "double", "%", "(c2 - c7) / c2 * 100", null)))
                .build();
        List<MetricValue> values = parser.parse(g, output, eval);
        assertEquals(1, values.size());
        // total=15884(c2), available=8890(c7) -> (15884-8890)/15884*100 = 44.03%
        double expected = (15884 - 8890) * 100.0 / 15884;
        assertEquals(expected, values.get(0).getDoubleValue(), 0.01);
    }

    @Test
    @DisplayName("table 解析: df -P 多分区, 每分区一条 + 设备标签 + 跳过表头")
    void parseDfTable() {
        String output = """
                Filesystem     1024-blocks    Used Available Capacity Mounted on
                /dev/sda1         51473868 8765432  40067892      18% /
                /dev/sdb1        103833648 52345678  46195970      54% /data
                tmpfs              8130004       0   8130004       0% /dev/shm
                """;
        CollectTemplate.MetricGroup g = CollectTemplate.MetricGroup.builder()
                .group("disk").parser("table").keyBy("device")
                .fields(List.of(field("usage_percent", "double", "%", null, 5)))
                .build();
        List<MetricValue> values = parser.parse(g, output, eval);
        // 3 个分区 (表头跳过)
        assertEquals(3, values.size(), "应解析出3个分区, 表头跳过");
        // 第一个分区 /dev/sda1 使用率 18%
        MetricValue first = values.get(0);
        assertEquals(18.0, first.getDoubleValue(), 0.001);
        assertNotNull(first.getLabels());
        assertEquals("/dev/sda1", first.getLabels().get("device"));
        // 第二个分区 54%
        assertEquals(54.0, values.get(1).getDoubleValue(), 0.001);
    }

    @Test
    @DisplayName("line 解析: /proc/loadavg 首列 load1")
    void parseLoadavg() {
        String output = "0.52 0.58 0.59 1/234 5678";
        CollectTemplate.MetricGroup g = CollectTemplate.MetricGroup.builder()
                .group("load").parser("line")
                .fields(List.of(field("load1", "double", null, "c1", null)))
                .build();
        List<MetricValue> values = parser.parse(g, output, eval);
        assertEquals(1, values.size());
        assertEquals(0.52, values.get(0).getDoubleValue(), 0.001);
    }

    @Test
    @DisplayName("line 解析: ss established 计数")
    void parseSsCount() {
        String output = "142";
        CollectTemplate.MetricGroup g = CollectTemplate.MetricGroup.builder()
                .group("tcp").parser("line")
                .fields(List.of(field("established", "long", null, "c1", null)))
                .build();
        List<MetricValue> values = parser.parse(g, output, eval);
        assertEquals(142.0, values.get(0).getDoubleValue(), 0.001);
    }

    @Test
    @DisplayName("空输出/空字段安全返回空列表 (不抛异常)")
    void emptyOutputSafe() {
        CollectTemplate.MetricGroup g = CollectTemplate.MetricGroup.builder()
                .group("cpu").parser("awk")
                .fields(List.of(field("usage_percent", "double", "%", "100 - c15", null)))
                .build();
        assertTrue(parser.parse(g, "", eval).isEmpty());
        assertTrue(parser.parse(g, null, eval).isEmpty());
    }
}
