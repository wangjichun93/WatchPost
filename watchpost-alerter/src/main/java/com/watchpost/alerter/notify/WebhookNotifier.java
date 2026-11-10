package com.watchpost.alerter.notify;

import com.watchpost.common.spi.Notifier;
import com.watchpost.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Webhook 通知实现 (PRD: 预留 Webhook 接口供后续扩展; IM/短信不在 v1)。
 * 激活: watchpost.notify.webhook.enabled=true 且配置了 urls。
 *
 * 把告警载荷 (subject/content/context) 以 JSON POST 到每个配置的 webhook URL。
 * 用 JDK 内置 java.net.http.HttpClient (无额外依赖); 超时 5s; 任一 URL 失败记录但不阻断其他。
 * 可选 HMAC-SHA256 签名头 (X-Watchpost-Signature), 便于接收方校验来源。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "watchpost.notify.webhook.enabled", havingValue = "true")
public class WebhookNotifier implements Notifier {

    @Value("${watchpost.notify.webhook.urls:}")
    private String urlsCsv;

    @Value("${watchpost.notify.webhook.secret:}")
    private String secret;

    @Value("${watchpost.notify.webhook.timeout-ms:5000}")
    private long timeoutMs;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String channel() {
        return "WEBHOOK";
    }

    @Override
    public NotifyResult send(List<String> recipients, String subject, String content, Map<String, Object> context) {
        List<String> urls = parseUrls(recipients);
        if (urls.isEmpty()) {
            return NotifyResult.fail("no webhook urls configured");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("channel", "WEBHOOK");
        payload.put("subject", subject);
        payload.put("content", content);
        payload.put("context", context == null ? Map.of() : context);
        payload.put("timestamp", System.currentTimeMillis());
        String body = JsonUtil.toJson(payload);

        int ok = 0;
        String lastError = null;
        for (String url : urls) {
            try {
                HttpRequest.Builder rb = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
                if (secret != null && !secret.isBlank()) {
                    rb.header("X-Watchpost-Signature", hmac(body));
                }
                HttpResponse<String> resp = httpClient.send(rb.build(), HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                    ok++;
                } else {
                    lastError = "HTTP " + resp.statusCode();
                    log.warn("webhook non-2xx from {}: {}", url, resp.statusCode());
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.warn("webhook send failed to {}: {}", url, e.getMessage());
            }
        }
        return ok > 0
                ? NotifyResult.ok("webhook:" + ok + "/" + urls.size())
                : NotifyResult.fail(lastError == null ? "all webhooks failed" : lastError);
    }

    /** URL 来源: 优先配置的 urls; 若 recipients 传入 http(s) URL 也接受 (灵活路由)。 */
    private List<String> parseUrls(List<String> recipients) {
        if (urlsCsv != null && !urlsCsv.isBlank()) {
            return java.util.Arrays.stream(urlsCsv.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if (recipients != null) {
            return recipients.stream()
                    .filter(r -> r != null && (r.startsWith("http://") || r.startsWith("https://")))
                    .toList();
        }
        return List.of();
    }

    private String hmac(String body) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : sig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("webhook hmac failed: {}", e.getMessage());
            return "";
        }
    }
}
