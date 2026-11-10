package com.watchpost.collector.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.watchpost.common.exception.BizException;
import com.watchpost.common.exception.ErrorCode;
import com.watchpost.common.model.CollectTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 采集模板加载器: 从 classpath:templates/*.yaml 加载内置模板, 反序列化为 CollectTemplate。
 * 支持热加载 (reload); 运行时也可从 wp_monitor_template.yaml_spec 解析实例级模板。
 */
@Slf4j
@Component
public class TemplateLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private final Map<String, CollectTemplate> cache = new HashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    /** 从 classpath:templates/*.yaml 重新加载全部内置模板。 */
    public synchronized void reload() {
        cache.clear();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:templates/*.yaml");
            for (Resource r : resources) {
                try (InputStream in = r.getInputStream()) {
                    CollectTemplate t = YAML.readValue(in, CollectTemplate.class);
                    if (t.getApp() != null) {
                        cache.put(t.getApp().name(), t);
                        log.info("loaded collect template: app={} protocol={} file={}",
                                t.getApp(), t.getProtocol(), r.getFilename());
                    }
                } catch (Exception e) {
                    log.error("failed to load template {}", r.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("template scan failed", e);
        }
        log.info("collect templates loaded: {} (apps={})", cache.size(), cache.keySet());
    }

    /** 按 app_type 取内置模板。 */
    public CollectTemplate get(String appType) {
        CollectTemplate t = cache.get(appType);
        if (t == null) throw new BizException(ErrorCode.TEMPLATE_INVALID, "no template for " + appType);
        return t;
    }

    /** 从 YAML 文本解析 (实例级自定义模板 / DB 存储的 yaml_spec)。 */
    public CollectTemplate parse(String yamlSpec) {
        try {
            return YAML.readValue(yamlSpec, CollectTemplate.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.TEMPLATE_INVALID, e);
        }
    }

    public Map<String, CollectTemplate> all() {
        return Map.copyOf(cache);
    }
}
