package com.watchpost.collector.protocol;

import com.watchpost.common.enums.FieldType;
import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MetricRecord;
import com.watchpost.common.model.MetricValue;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.ProtocolCollector;
import com.watchpost.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.Proxy;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP(S) API 嗅探采集器 (PRD 03: 应用 API 主动探测)。
 *
 * 采集: 可用性 / 响应耗时 / 5xx / JSONPath 断言 / SSL 证书剩余天数 / 分段耗时(DNS·连接·首字节) / 关键字 / 响应大小。
 * 硬超时 target.timeoutMs (≤5s); OkHttp EventListener 采集分段耗时。
 *
 * per-monitor 探测配置来自 wp_monitor.extra_json (前端 HTTP 表单), 支持:
 *   method   GET/POST/PUT/DELETE/HEAD (默认 GET)
 *   path     追加到 host 后的路径 (如 /api/v1/health)
 *   headers  请求头 (Map 或 JSON 字符串)
 *   body     请求体 (POST/PUT/PATCH; 默认 application/json)
 *   contentType 请求体类型 (默认 application/json)
 *   expectStatus 期望状态码 (List 或逗号串; 缺省 2xx)
 * URL 由 host + port + path 组合, 正确处理 host 已含 scheme / 尾部斜杠 / 默认端口, 不再依赖模板占位符。
 */
@Slf4j
@Component
public class HttpCollector implements ProtocolCollector {

    private final OkHttpClient sharedClient;

    public HttpCollector() {
        this.sharedClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .followRedirects(true)
                .build();
    }

    @Override
    public ProtocolType protocol() {
        return ProtocolType.http;
    }

    @Override
    public MetricRecord collect(MonitorTarget target) {
        long t0 = System.currentTimeMillis();
        MetricRecord.MetricRecordBuilder rb = MetricRecord.builder()
                .monitorId(target.getMonitorId()).monitorName(target.getMonitorName())
                .appType(target.getAppType()).protocol(ProtocolType.http).timestamp(t0);

        CollectTemplate tpl = target.getTemplate();
        CollectTemplate.HttpRequest req = tpl != null ? tpl.getRequest() : null;
        CollectTemplate.HttpChecks checks = tpl != null ? tpl.getChecks() : null;
        Map<String, Object> extra = target.getExtra() == null ? Map.of() : target.getExtra();
        List<Integer> expectStatus = extraExpectStatus(extra, req);

        OkHttpClient client = sharedClient.newBuilder()
                .callTimeout(Duration.ofMillis(target.getTimeoutMs()))
                .build();
        Request request;
        try {
            request = buildRequest(target, req);
        } catch (Exception e) {
            return rb.success(false).error("invalid request: " + e.getMessage())
                    .costMs(System.currentTimeMillis() - t0).values(List.of()).build();
        }

        List<MetricValue> values = new ArrayList<>();
        TimingListener timing = new TimingListener();
        try (Response response = client.newBuilder().eventListener(timing).build().newCall(request).execute()) {
            int code = response.code();
            long cost = System.currentTimeMillis() - t0;
            String body = response.body() != null ? response.body().string() : "";

            values.add(num("api", "status_code", code, null));
            values.add(bool("api", "available", isExpected(code, expectStatus)));
            values.add(num("api", "response_ms", cost, "ms"));
            values.add(num("api", "status_5xx", code >= 500 && code < 600 ? 1 : 0, null));
            values.add(num("api", "body_size", body.length(), "B"));

            // 分段耗时 (DNS/连接/首字节)
            if (timing.dnsMs >= 0) values.add(num("api", "dns_ms", timing.dnsMs, "ms"));
            if (timing.connectMs >= 0) values.add(num("api", "connect_ms", timing.connectMs, "ms"));
            if (timing.ttfbMs >= 0) values.add(num("api", "ttfb_ms", timing.ttfbMs, "ms"));

            // SSL 证书剩余天数
            if (checks != null && checks.isSslCertDays()) {
                Long days = sslCertDays(response);
                if (days != null) values.add(num("api", "ssl_cert_days", days, "d"));
            }
            // JSONPath 断言
            if (checks != null && checks.getJsonPathAssert() != null && !checks.getJsonPathAssert().isEmpty()) {
                boolean pass = evalJsonAssert(body, checks.getJsonPathAssert());
                values.add(bool("api", "json_assert_pass", pass));
                values.add(num("api", "json_assert_fail", pass ? 0 : 1, null));
            }
            // 响应体关键字
            if (checks != null && checks.getBodyKeyword() != null) {
                boolean has = body.contains(checks.getBodyKeyword());
                values.add(num("api", "body_keyword_missing", has ? 0 : 1, null));
            }
            return rb.success(true).costMs(cost).values(values).build();
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - t0;
            values.add(bool("api", "available", false));
            values.add(num("api", "response_ms", cost, "ms"));
            return rb.success(false).error(e.getMessage()).costMs(cost).values(values).build();
        }
    }

    @Override
    public boolean precheck(MonitorTarget target) {
        CollectTemplate tpl = target.getTemplate();
        CollectTemplate.HttpRequest req = tpl != null ? tpl.getRequest() : null;
        Map<String, Object> extra = target.getExtra() == null ? Map.of() : target.getExtra();
        List<Integer> expectStatus = extraExpectStatus(extra, req);
        Request request;
        try {
            request = buildRequest(target, req);
        } catch (Exception e) {
            log.info("HTTP precheck invalid request: {}", e.getMessage());
            return false;
        }
        try (Response r = sharedClient.newBuilder()
                .callTimeout(Duration.ofMillis(target.getTimeoutMs())).build()
                .newCall(request).execute()) {
            int code = r.code();
            // 配置了期望状态码: 必须命中; 否则能连通(非5xx)即通过
            if (!expectStatus.isEmpty()) return expectStatus.contains(code);
            return code < 500;
        } catch (Exception e) {
            log.info("HTTP precheck failed {} -> {}", resolveUrl(target), e.getMessage());
            return false;
        }
    }

    // ==================== URL / Request 构造 ====================

    /**
     * 解析探测 URL: host(+scheme) + port + extra.path 组合。
     *  - host 已含 scheme (https://x) → 直接用; 否则按端口推断 (443→https, 其余→http)
     *  - 去掉 base 尾部斜杠, 避免 "//" 重复
     *  - base 未含显式端口 且 port 非该 scheme 默认端口时才追加 (避免 https://x/:443 畸形 URL)
     *  - extra.path 追加 (自动补前导 /)
     */
    private String resolveUrl(MonitorTarget target) {
        Map<String, Object> extra = target.getExtra() == null ? Map.of() : target.getExtra();
        String path = extraStr(extra, "path");
        String host = target.getHost() == null ? "" : target.getHost().trim();
        Integer port = target.getPort();

        String base;
        if (host.matches("(?i)^https?://.*")) {
            base = host;
        } else {
            String scheme = (port != null && port == 443) ? "https" : "http";
            base = scheme + "://" + host;
        }
        base = base.replaceAll("/+$", "");

        try {
            URI u = URI.create(base);
            if (u.getPort() == -1 && port != null) {
                boolean isDefault = (port == 443 && "https".equalsIgnoreCase(u.getScheme()))
                        || (port == 80 && "http".equalsIgnoreCase(u.getScheme()));
                if (!isDefault && u.getHost() != null) {
                    base = u.getScheme() + "://" + u.getHost() + ":" + port
                            + (u.getPath() == null || u.getPath().isEmpty() ? "" : u.getPath());
                }
            }
        } catch (Exception ignored) {}

        if (path != null && !path.isBlank()) {
            if (!path.startsWith("/")) path = "/" + path;
            base = base + path;
        }
        return base;
    }

    /** 构造 OkHttp 请求: method/path/headers/body/Authorization 均支持 per-monitor extra 覆写。 */
    private Request buildRequest(MonitorTarget target, CollectTemplate.HttpRequest req) {
        Map<String, Object> extra = target.getExtra() == null ? Map.of() : target.getExtra();
        String url = resolveUrl(target);

        String method = extraStr(extra, "method");
        if (method == null || method.isBlank()) {
            method = (req != null && req.getMethod() != null) ? req.getMethod() : "GET";
        }
        method = method.toUpperCase();

        String contentType = extraStr(extra, "contentType");
        if (contentType == null || contentType.isBlank()) contentType = "application/json";
        String body = extraStr(extra, "body");
        if (body == null && req != null) body = req.getBody();

        RequestBody rb = null;
        if (body != null && !body.isBlank() && !method.equals("GET") && !method.equals("HEAD")) {
            rb = RequestBody.create(body, MediaType.parse(contentType));
        } else if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
            rb = RequestBody.create("", MediaType.parse(contentType));   // 这些方法 OkHttp 要求非空 body
        }
        Request.Builder b = new Request.Builder().url(url).method(method, rb);

        // headers: 模板默认 → extra 覆写 → 凭据 token 注入 Authorization (若未显式设置)
        Map<String, String> headers = new LinkedHashMap<>();
        if (req != null && req.getHeaders() != null) headers.putAll(req.getHeaders());
        headers.putAll(extraHeaders(extra));
        headers.forEach((k, v) -> b.header(k, injectCred(v, target)));

        String token = target.getCredential() == null ? null : target.getCredential().get("token");
        boolean hasAuth = headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Authorization"));
        if (token != null && !token.isBlank() && !hasAuth) {
            b.header("Authorization", "Bearer " + injectCred(token, target));
        }
        return b.build();
    }

    // ==================== extra 读取辅助 ====================

    private String extraStr(Map<String, Object> extra, String key) {
        Object v = extra.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** extra.headers 支持 Map 或 JSON 字符串两种形态。 */
    private Map<String, String> extraHeaders(Map<String, Object> extra) {
        Map<String, String> out = new LinkedHashMap<>();
        Object h = extra.get("headers");
        if (h instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        } else if (h instanceof String s && !s.isBlank()) {
            try {
                JsonUtil.toMap(s).forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** extra.expectStatus 支持 List 或逗号字符串; 缺省回退模板 expectStatus。 */
    private List<Integer> extraExpectStatus(Map<String, Object> extra, CollectTemplate.HttpRequest req) {
        List<Integer> out = new ArrayList<>();
        Object e = extra.get("expectStatus");
        if (e instanceof List<?> l) {
            l.forEach(x -> { try { out.add(Integer.parseInt(String.valueOf(x))); } catch (Exception ignored) {} });
        } else if (e instanceof String s && !s.isBlank()) {
            for (String part : s.split(",")) {
                try { out.add(Integer.parseInt(part.trim())); } catch (Exception ignored) {}
            }
        }
        if (out.isEmpty() && req != null && req.getExpectStatus() != null) out.addAll(req.getExpectStatus());
        return out;
    }

    private String injectCred(String s, MonitorTarget target) {
        if (s == null || target.getCredential() == null) return s;
        String out = s;
        for (Map.Entry<String, String> e : target.getCredential().entrySet()) {
            out = out.replace("${cred." + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private boolean isExpected(int code, List<Integer> expectStatus) {
        if (expectStatus == null || expectStatus.isEmpty()) {
            return code >= 200 && code < 300;
        }
        return expectStatus.contains(code);
    }

    // ==================== 断言 / 证书 / 分段耗时 ====================

    private boolean evalJsonAssert(String body, List<CollectTemplate.JsonAssert> asserts) {
        try {
            Object doc = com.jayway.jsonpath.Configuration.defaultConfiguration().jsonProvider().parse(body);
            for (CollectTemplate.JsonAssert a : asserts) {
                Object actual = com.jayway.jsonpath.JsonPath.read(doc, a.getPath());
                if (a.getExpect() != null && !String.valueOf(actual).equals(String.valueOf(a.getExpect()))) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Long sslCertDays(Response response) {
        try {
            Handshake hs = response.handshake();
            if (hs == null) return null;
            for (java.security.cert.Certificate c : hs.peerCertificates()) {
                if (c instanceof X509Certificate x509) {
                    long end = x509.getNotAfter().getTime();
                    return TimeUnit.MILLISECONDS.toDays(end - System.currentTimeMillis());
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private MetricValue num(String group, String field, double value, String unit) {
        return MetricValue.builder().metricGroup(group).fieldName(field)
                .fieldType(FieldType.DOUBLE).doubleValue(value).unit(unit).build();
    }

    private MetricValue bool(String group, String field, boolean value) {
        return MetricValue.builder().metricGroup(group).fieldName(field)
                .fieldType(FieldType.BOOL).doubleValue(value ? 1.0 : 0.0).strValue(String.valueOf(value)).build();
    }

    /** OkHttp 事件监听: 采集 DNS/连接/首字节分段耗时。 */
    private static class TimingListener extends EventListener {
        long dnsStart = -1, dnsMs = -1;
        long connectStart = -1, connectMs = -1;
        long callStart = -1, ttfbMs = -1;

        @Override public void callStart(Call call) { callStart = System.nanoTime(); }
        @Override public void dnsStart(Call call, String domainName) { dnsStart = System.nanoTime(); }
        @Override public void dnsEnd(Call call, String domainName, List<java.net.InetAddress> inetAddressList) {
            if (dnsStart > 0) dnsMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - dnsStart);
        }
        @Override public void connectStart(Call call, java.net.InetSocketAddress inetSocketAddress, Proxy proxy) {
            connectStart = System.nanoTime();
        }
        @Override public void connectEnd(Call call, java.net.InetSocketAddress inetSocketAddress, Proxy proxy, Protocol protocol) {
            if (connectStart > 0) connectMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - connectStart);
        }
        @Override public void responseHeadersStart(Call call) {
            if (callStart > 0) ttfbMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callStart);
        }
    }
}
