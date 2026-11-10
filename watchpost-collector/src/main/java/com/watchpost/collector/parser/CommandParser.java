package com.watchpost.collector.parser;

import com.watchpost.common.enums.FieldType;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MetricValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 命令输出解析器: 把 SSH 只读命令的原始 stdout 按模板字段定义解析为 MetricValue。
 * 支持 parser: line(单值) / regex(命名组) / awk(按列) / table(多行按列, keyBy 分组)。
 * 单位换算 expr 由 RuleExpression (JEXL) 在采集器内求值。
 */
@Slf4j
@Component
public class CommandParser {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * 解析一个指标组的命令输出。
     * @param group   模板指标组定义
     * @param output  命令原始 stdout
     * @param exprEval 表达式求值回调 (expr -> bindings -> Double), 由采集器注入 (JEXL)
     * @return 解析出的字段值列表
     */
    public List<MetricValue> parse(CollectTemplate.MetricGroup group, String output, ExprEvaluator exprEval) {
        List<MetricValue> out = new ArrayList<>();
        if (output == null || output.isBlank() || group.getFields() == null) return out;
        String parser = group.getParser() == null ? "line" : group.getParser().toLowerCase();
        switch (parser) {
            case "regex" -> out.addAll(parseRegex(group, output, exprEval));
            case "awk"   -> out.addAll(parseAwk(group, output, exprEval));
            case "table" -> out.addAll(parseTable(group, output, exprEval));
            default      -> out.addAll(parseLine(group, output, exprEval));
        }
        return out;
    }

    /** line: 整段输出作为单值, 按字段 expr 或首行数字解析。 */
    private List<MetricValue> parseLine(CollectTemplate.MetricGroup group, String output, ExprEvaluator eval) {
        List<MetricValue> out = new ArrayList<>();
        String firstLine = output.lines().findFirst().orElse("").trim();
        Map<String, Object> vars = baseVars(output, firstLine);
        for (CollectTemplate.FieldDef f : group.getFields()) {
            out.add(buildValue(group.getGroup(), f, vars, eval, null));
        }
        return out;
    }

    /** regex: 用字段名作为命名捕获组 (?<name>...) 或 expr 中引用。 */
    private List<MetricValue> parseRegex(CollectTemplate.MetricGroup group, String output, ExprEvaluator eval) {
        List<MetricValue> out = new ArrayList<>();
        Map<String, Object> vars = baseVars(output, output.lines().findFirst().orElse("").trim());
        // 尝试用字段名做命名组匹配
        for (CollectTemplate.FieldDef f : group.getFields()) {
            Matcher m = Pattern.compile("(?<" + f.getName() + ">[0-9.]+)").matcher(output);
            if (m.find()) {
                vars.put(f.getName(), parseNum(m.group(f.getName())));
            }
        }
        for (CollectTemplate.FieldDef f : group.getFields()) {
            out.add(buildValue(group.getGroup(), f, vars, eval, null));
        }
        return out;
    }

    /** awk: 按空白分列, 字段 column 指定取第几列 (1-based); 默认取首行。 */
    private List<MetricValue> parseAwk(CollectTemplate.MetricGroup group, String output, ExprEvaluator eval) {
        List<MetricValue> out = new ArrayList<>();
        String line = output.lines().filter(l -> !l.isBlank()).findFirst().orElse("");
        String[] cols = WHITESPACE.split(line.trim());
        Map<String, Object> vars = baseVars(output, line);
        for (int i = 0; i < cols.length; i++) vars.put("c" + (i + 1), parseNum(cols[i]));
        for (CollectTemplate.FieldDef f : group.getFields()) {
            if (f.getColumn() != null && f.getColumn() >= 1 && f.getColumn() <= cols.length) {
                vars.put(f.getName(), parseNum(cols[f.getColumn() - 1]));
            }
            out.add(buildValue(group.getGroup(), f, vars, eval, null));
        }
        return out;
    }

    /** table: 多行, 每行一条记录 (如 df 每分区); keyBy 指定标签列。 */
    private List<MetricValue> parseTable(CollectTemplate.MetricGroup group, String output, ExprEvaluator eval) {
        List<MetricValue> out = new ArrayList<>();
        List<String> lines = output.lines().filter(l -> !l.isBlank()).toList();
        // 跳过表头 (首行含非数字标题)
        int start = looksLikeHeader(lines.isEmpty() ? "" : lines.get(0)) ? 1 : 0;
        for (int i = start; i < lines.size(); i++) {
            String[] cols = WHITESPACE.split(lines.get(i).trim());
            Map<String, Object> vars = new HashMap<>();
            for (int c = 0; c < cols.length; c++) vars.put("c" + (c + 1), parseNum(cols[c]));
            vars.put("row", lines.get(i));
            Map<String, String> labels = new HashMap<>();
            if (group.getKeyBy() != null) {
                // keyBy 列名约定: 第1列为 key (如 mountpoint)
                labels.put(group.getKeyBy(), cols[0]);
                vars.put(group.getKeyBy(), cols[0]);
            }
            for (CollectTemplate.FieldDef f : group.getFields()) {
                if (f.getColumn() != null && f.getColumn() >= 1 && f.getColumn() <= cols.length) {
                    vars.put(f.getName(), parseNum(stripPercent(cols[f.getColumn() - 1])));
                }
                out.add(buildValue(group.getGroup(), f, vars, eval, labels.isEmpty() ? null : labels));
            }
        }
        return out;
    }

    private Map<String, Object> baseVars(String output, String firstLine) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("output", output);
        vars.put("line", firstLine);
        String[] cols = WHITESPACE.split(firstLine.trim());
        for (int i = 0; i < cols.length; i++) vars.put("c" + (i + 1), parseNum(cols[i]));
        return vars;
    }

    private MetricValue buildValue(String group, CollectTemplate.FieldDef f, Map<String, Object> vars,
                                   ExprEvaluator eval, Map<String, String> labels) {
        FieldType type = fieldType(f.getType());
        MetricValue.MetricValueBuilder b = MetricValue.builder()
                .metricGroup(group).fieldName(f.getName()).fieldType(type).unit(f.getUnit()).labels(labels);
        Object val = null;
        if (f.getExpr() != null && !f.getExpr().isBlank()) {
            try { val = eval.eval(f.getExpr(), vars); }
            catch (Exception e) { log.debug("expr eval failed: {} -> {}", f.getExpr(), e.getMessage()); }
        } else if (vars.containsKey(f.getName())) {
            val = vars.get(f.getName());
        }
        if (type.isNumeric()) {
            b.doubleValue(toDouble(val));
        } else {
            b.strValue(val == null ? null : String.valueOf(val));
        }
        return b.build();
    }

    private boolean looksLikeHeader(String line) {
        if (line == null || line.isBlank()) return false;
        // 表头: 含字母且不以数字开头 (如 "Filesystem 1K-blocks Used ...")
        String first = WHITESPACE.split(line.trim())[0];
        return first.matches(".*[a-zA-Z].*") && !first.matches("^[0-9.]+%?$");
    }

    private String stripPercent(String s) {
        return s == null ? null : s.replace("%", "").trim();
    }

    private Double parseNum(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(stripPercent(s)); }
        catch (NumberFormatException e) { return null; }
    }

    private Double toDouble(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); }
        catch (NumberFormatException e) { return null; }
    }

    private FieldType fieldType(String t) {
        if (t == null) return FieldType.DOUBLE;
        return switch (t.toLowerCase()) {
            case "long" -> FieldType.LONG;
            case "int" -> FieldType.INT;
            case "string" -> FieldType.STRING;
            case "bool", "boolean" -> FieldType.BOOL;
            default -> FieldType.DOUBLE;
        };
    }

    /** 表达式求值回调 (由采集器注入 JEXL 实现)。 */
    public interface ExprEvaluator {
        Object eval(String expression, Map<String, Object> bindings);
    }
}
