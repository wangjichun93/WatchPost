package com.watchpost.collector.schedule;

import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.CryptoService;
import com.watchpost.collector.template.TemplateLoader;
import com.watchpost.warehouse.entity.CredentialEntity;
import com.watchpost.warehouse.entity.MonitorEntity;
import com.watchpost.warehouse.repository.CredentialRepository;
import com.watchpost.warehouse.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 默认目标提供者: 从 warehouse 组装 MonitorTarget。
 * 凭据在此解密为瞬时明文注入 MonitorTarget.credential (用后即弃, 不持久化/不进日志)。
 * 模板优先取实例自定义 (wp_monitor_template.yaml_spec), 否则取内置 classpath 模板。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarehouseTargetProvider implements TargetProvider {

    private final MonitorRepository monitorRepo;
    private final CredentialRepository credentialRepo;
    private final TemplateLoader templateLoader;
    private final CryptoService crypto;
    private final CollectorProperties props;

    @Override
    public List<MonitorTarget> loadEnabled() {
        List<MonitorEntity> monitors = monitorRepo.findByDeletedFalseAndEnabledTrue();
        List<MonitorTarget> targets = new ArrayList<>(monitors.size());
        for (MonitorEntity m : monitors) {
            assemble(m).ifPresent(targets::add);
        }
        log.info("target provider loaded {} enabled monitors", targets.size());
        return targets;
    }

    @Override
    public Optional<MonitorTarget> load(long monitorId) {
        return monitorRepo.findByIdAndDeletedFalse(monitorId).flatMap(this::assemble);
    }

    private Optional<MonitorTarget> assemble(MonitorEntity m) {
        try {
            CollectTemplate tpl = templateLoader.get(m.getAppType().name());
            Map<String, String> cred = decryptCredential(m.getId());
            long timeout = tpl.getTimeout() > 0 ? tpl.getTimeout() * 1000L : props.getDefaultTimeoutMs();
            // 扩展配置 (HTTP: method/path/headers/body/expectStatus)
            Map<String, Object> extra = new HashMap<>();
            if (m.getExtraJson() != null && !m.getExtraJson().isBlank()) {
                try {
                    extra.putAll(com.watchpost.common.util.JsonUtil.toMap(m.getExtraJson()));
                } catch (Exception ignored) {}
            }
            MonitorTarget target = MonitorTarget.builder()
                    .monitorId(m.getId())
                    .monitorName(m.getName())
                    .appType(m.getAppType())
                    .protocol(m.getProtocol())
                    .host(m.getHost())
                    .port(m.getPort())
                    .intervalSec(m.getIntervalSec() == null ? 30 : m.getIntervalSec())
                    .template(tpl)
                    .credential(cred)
                    .tags(Map.of())
                    .extra(extra)
                    .timeoutMs(timeout)
                    .build();
            return Optional.of(target);
        } catch (Exception e) {
            log.error("assemble target failed monitorId={}: {}", m.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Map<String, String> decryptCredential(long monitorId) {
        Map<String, String> cred = new HashMap<>();
        Optional<CredentialEntity> opt = credentialRepo.findByMonitorId(monitorId);
        if (opt.isEmpty()) return cred;
        CredentialEntity c = opt.get();
        if (c.getUsername() != null) cred.put("username", c.getUsername());
        // 解密敏感 JSON blob (password / privateKey / passphrase / token / community)
        try {
            String json = crypto.decrypt(c.getCipherText());
            Map<String, Object> sensitive = com.watchpost.common.util.JsonUtil.toMap(json);
            sensitive.forEach((k, v) -> { if (v != null) cred.put(k, String.valueOf(v)); });
            // 兼容: password 同时暴露为 secret (部分采集器读 secret)
            if (cred.containsKey("password") && !cred.containsKey("secret")) {
                cred.put("secret", cred.get("password"));
            }
        } catch (Exception e) {
            log.error("credential decrypt failed monitorId={}: {}", monitorId, e.getMessage());
        }
        // 合并非敏感额外参数 (SNMP version / IPMI cipherSuite 等)
        if (c.getExtraJson() != null && !c.getExtraJson().isBlank()) {
            try {
                com.watchpost.common.util.JsonUtil.toMap(c.getExtraJson())
                        .forEach((k, v) -> { if (v != null) cred.put(k, String.valueOf(v)); });
            } catch (Exception ignored) {}
        }
        return cred;
    }
}
