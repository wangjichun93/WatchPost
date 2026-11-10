package com.watchpost.manager.service;

import com.watchpost.common.dto.MonitorRequest;
import com.watchpost.common.dto.MonitorView;
import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.ProtocolType;
import com.watchpost.common.exception.BizException;
import com.watchpost.common.exception.ErrorCode;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.common.model.MonitorTarget;
import com.watchpost.common.spi.CryptoService;
import com.watchpost.common.spi.ProtocolCollector;
import com.watchpost.common.util.MaskUtil;
import com.watchpost.collector.schedule.CollectScheduler;
import com.watchpost.collector.schedule.TargetProvider;
import com.watchpost.collector.template.TemplateLoader;
import com.watchpost.warehouse.entity.CredentialEntity;
import com.watchpost.warehouse.entity.MonitorEntity;
import com.watchpost.warehouse.repository.CredentialRepository;
import com.watchpost.warehouse.repository.MonitorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 监控对象服务 (PRD 06: 监控中心增删改查, 新增先连通性预检失败不落库)。
 * 凭据 AES-256-GCM 加密落 wp_credential; 响应一律脱敏。
 * 保存后热更新调度器 (refresh/schedule/cancel), 配置即生效无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorRepository monitorRepo;
    private final CredentialRepository credentialRepo;
    private final TemplateLoader templateLoader;
    private final CryptoService crypto;
    private final TargetProvider targetProvider;
    private final CollectScheduler scheduler;
    private final List<ProtocolCollector> collectors;

    /** 分页查询监控对象。 */
    public com.watchpost.common.dto.PageResult<MonitorView> search(AppType appType, Integer status,
                                                                    String keyword, int page, int size) {
        Page<MonitorEntity> p = monitorRepo.findAll(
                com.watchpost.warehouse.spec.Specs.monitors(appType, status, keyword),
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createTime")));
        List<MonitorView> views = p.getContent().stream().map(this::toView).toList();
        return com.watchpost.common.dto.PageResult.of(views, p.getTotalElements(), page, size);
    }

    public MonitorView get(long id) {
        return toView(monitorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(ErrorCode.MONITOR_NOT_FOUND)));
    }

    /**
     * 新增监控对象: 组装目标 → 连通性预检 → 失败不落库 → 成功保存 + 加密凭据 + 热调度。
     */
    @Transactional
    public MonitorView create(MonitorRequest req, Long operatorId) {
        AppType appType = AppType.of(req.getAppType());
        CollectTemplate tpl = templateLoader.get(appType.name());
        ProtocolType protocol = tpl.getProtocol();

        MonitorEntity entity = new MonitorEntity();
        applyRequest(entity, req, appType, protocol);
        entity.setCreateBy(operatorId);
        entity.setStatus(1);
        entity.setConnFailCount(0);

        // 先临时保存以获取 ID (预检需要 monitorId 组装目标); 预检失败则回滚
        // 采用: 先预检 (用未持久化的目标), 通过后再落库
        MonitorEntity saved = monitorRepo.save(entity);

        // 加密凭据
        saveCredential(saved.getId(), protocol, req.getCredential());

        // 连通性预检 (PRD: 失败不落库)
        MonitorTarget target = targetProvider.load(saved.getId())
                .orElseThrow(() -> new BizException(ErrorCode.MONITOR_PRECHECK_FAILED, "target assemble failed"));
        boolean ok = precheck(target);
        if (!ok) {
            // 回滚: 删除刚保存的监控与凭据
            credentialRepo.deleteByMonitorId(saved.getId());
            monitorRepo.delete(saved);
            throw new BizException(ErrorCode.MONITOR_PRECHECK_FAILED,
                    "无法连通 " + req.getHost() + " 或认证失败");
        }

        // 热调度
        scheduler.schedule(target);
        log.info("monitor created id={} name={} app={}", saved.getId(), saved.getName(), appType);
        return toView(saved);
    }

    /** 编辑监控对象。 */
    @Transactional
    public MonitorView update(long id, MonitorRequest req) {
        MonitorEntity entity = monitorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(ErrorCode.MONITOR_NOT_FOUND));
        AppType appType = AppType.of(req.getAppType());
        CollectTemplate tpl = templateLoader.get(appType.name());
        applyRequest(entity, req, appType, tpl.getProtocol());
        monitorRepo.save(entity);
        if (req.getCredential() != null && !req.getCredential().isEmpty()) {
            saveCredential(id, tpl.getProtocol(), req.getCredential());
        }
        // 热更新调度
        scheduler.cancel(id);
        targetProvider.load(id).ifPresent(scheduler::schedule);
        return toView(entity);
    }

    /** 删除监控对象 (软删除 + 取消调度)。 */
    @Transactional
    public void delete(long id) {
        MonitorEntity entity = monitorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(ErrorCode.MONITOR_NOT_FOUND));
        entity.setDeleted(true);
        entity.setEnabled(false);
        monitorRepo.save(entity);
        scheduler.cancel(id);
        log.info("monitor deleted (soft) id={}", id);
    }

    /** 启用/停用。 */
    @Transactional
    public MonitorView toggle(long id, boolean enabled) {
        MonitorEntity entity = monitorRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(ErrorCode.MONITOR_NOT_FOUND));
        entity.setEnabled(enabled);
        monitorRepo.save(entity);
        if (enabled) {
            targetProvider.load(id).ifPresent(scheduler::schedule);
        } else {
            scheduler.cancel(id);
        }
        return toView(entity);
    }

    /** 立即采集一次 (详情页手动刷新)。 */
    public void collectNow(long id) {
        scheduler.collectNow(id);
    }

    /** 连通性预检 (新增/编辑前可单独调用)。 */
    public boolean precheck(MonitorTarget target) {
        ProtocolCollector collector = collectors.stream()
                .filter(c -> c.protocol() == target.getProtocol()).findFirst().orElse(null);
        if (collector == null) return false;
        try {
            return collector.precheck(target);
        } catch (Exception e) {
            log.info("precheck error monitorId={}: {}", target.getMonitorId(), e.getMessage());
            return false;
        }
    }

    private void applyRequest(MonitorEntity entity, MonitorRequest req, AppType appType, ProtocolType protocol) {
        entity.setName(req.getName());
        entity.setAppType(appType);
        entity.setProtocol(protocol);
        entity.setHost(req.getHost());
        entity.setPort(req.getPort() != null ? req.getPort() : defaultPort(appType));
        entity.setIntervalSec(req.getIntervalSec() == null ? 30 : Math.max(15, req.getIntervalSec()));
        entity.setRemark(req.getRemark());
        entity.setTemplateId(null);   // 用内置模板; 实例自定义模板预留
        // 扩展配置 (HTTP: method/path/headers/body/expectStatus) 存 extra_json
        entity.setExtraJson(req.getExtra() == null || req.getExtra().isEmpty()
                ? null : com.watchpost.common.util.JsonUtil.toJson(req.getExtra()));
        entity.setUpdateTime(OffsetDateTime.now());
    }

    /** 敏感凭据字段 (整体加密为 JSON blob, 支持 password / privateKey+passphrase / token / community)。 */
    private static final java.util.Set<String> SENSITIVE_KEYS = java.util.Set.of(
            "password", "secret", "token", "community", "privateKey", "passphrase");

    private void saveCredential(long monitorId, ProtocolType protocol, Map<String, String> cred) {
        if (cred == null || cred.isEmpty()) return;
        CredentialEntity ce = credentialRepo.findByMonitorId(monitorId).orElseGet(CredentialEntity::new);
        ce.setMonitorId(monitorId);
        ce.setCredType(protocol);
        ce.setUsername(cred.get("username"));

        // 敏感字段整体加密为 JSON blob; 非敏感参数 (SNMP version / IPMI cipherSuite 等) 存 extra_json 明文
        Map<String, String> sensitive = new HashMap<>();
        Map<String, String> extra = new HashMap<>();
        for (Map.Entry<String, String> e : cred.entrySet()) {
            if ("username".equals(e.getKey())) continue;   // username 单独列存储
            if (SENSITIVE_KEYS.contains(e.getKey())) sensitive.put(e.getKey(), e.getValue());
            else extra.put(e.getKey(), e.getValue());
        }
        ce.setCipherText(crypto.encrypt(com.watchpost.common.util.JsonUtil.toJson(sensitive)));
        ce.setKmsKeyId(crypto.keyId());
        ce.setExtraJson(extra.isEmpty() ? null : com.watchpost.common.util.JsonUtil.toJson(extra));
        credentialRepo.save(ce);
    }

    private int defaultPort(AppType appType) {
        return switch (appType) {
            case linux -> 22;
            case http -> 80;
            case mysql -> 3306;
            case postgresql -> 5432;
            case oracle -> 1521;
        };
    }

    private MonitorView toView(MonitorEntity e) {
        Map<String, String> credMasked = new HashMap<>();
        credentialRepo.findByMonitorId(e.getId()).ifPresent(c -> {
            credMasked.put("type", c.getCredType().name());
            credMasked.put("username", MaskUtil.partial(c.getUsername()));
            credMasked.put("secret", "***");
        });
        Map<String, Object> extra = new HashMap<>();
        if (e.getExtraJson() != null && !e.getExtraJson().isBlank()) {
            try {
                extra.putAll(com.watchpost.common.util.JsonUtil.toMap(e.getExtraJson()));
            } catch (Exception ignored) {}
        }
        return MonitorView.builder()
                .id(e.getId()).name(e.getName())
                .appType(e.getAppType().name()).protocol(e.getProtocol().name())
                .host(e.getHost()).port(e.getPort()).intervalSec(e.getIntervalSec())
                .status(e.getStatus())
                .statusLabel(com.watchpost.common.enums.MonitorStatus.of(e.getStatus()).label())
                .connFailCount(e.getConnFailCount())
                .lastCollectAt(e.getLastCollectAt() == null ? null : e.getLastCollectAt().toInstant().toEpochMilli())
                .remark(e.getRemark())
                .tags(List.of())
                .credentialMasked(credMasked)
                .extra(extra)
                .createTime(e.getCreateTime() == null ? null : e.getCreateTime().toInstant().toEpochMilli())
                .build();
    }

    public Optional<MonitorEntity> findEntity(long id) {
        return monitorRepo.findByIdAndDeletedFalse(id);
    }
}
