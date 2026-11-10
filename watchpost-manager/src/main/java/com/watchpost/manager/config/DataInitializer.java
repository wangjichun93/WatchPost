package com.watchpost.manager.config;

import com.watchpost.collector.template.TemplateLoader;
import com.watchpost.common.enums.AppType;
import com.watchpost.common.enums.RoleCode;
import com.watchpost.common.model.CollectTemplate;
import com.watchpost.warehouse.entity.MonitorTemplateEntity;
import com.watchpost.warehouse.entity.RoleEntity;
import com.watchpost.warehouse.entity.UserEntity;
import com.watchpost.warehouse.repository.MonitorTemplateRepository;
import com.watchpost.warehouse.repository.RoleRepository;
import com.watchpost.warehouse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Set;

/**
 * 数据初始化器 (首次启动):
 * 1. 确保默认管理员账号存在 (口令 bcrypt 哈希, 来自配置 watchpost.bootstrap.admin-password)。
 *    说明: admin 用户由 Java 初始化器写入而非 SQL 种子, 保证 bcrypt 哈希正确 + 支持首登改密。
 * 2. 把 classpath:templates/*.yaml 内置采集模板同步到 wp_monitor_template (热加载来源)。
 *
 * 幂等: 已存在则跳过/更新, 可重复执行。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final MonitorTemplateRepository templateRepo;
    private final TemplateLoader templateLoader;
    private final PasswordEncoder passwordEncoder;

    @Value("${watchpost.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${watchpost.bootstrap.admin-password:admin123}")
    private String adminPassword;

    @Bean
    public ApplicationRunner initData() {
        return args -> {
            ensureRoles();
            ensureAdmin();
            syncTemplates();
        };
    }

    private void ensureRoles() {
        for (RoleCode code : RoleCode.values()) {
            if (roleRepo.findByCode(code).isEmpty()) {
                RoleEntity r = new RoleEntity();
                r.setCode(code);
                r.setName(code.label());
                roleRepo.save(r);
                log.info("seeded role: {}", code);
            }
        }
    }

    private void ensureAdmin() {
        if (userRepo.findByUsername(adminUsername).isPresent()) {
            log.info("admin user '{}' already exists, skip", adminUsername);
            return;
        }
        RoleEntity adminRole = roleRepo.findByCode(RoleCode.ADMIN).orElse(null);
        UserEntity admin = new UserEntity();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("系统管理员");
        admin.setEnabled(true);
        if (adminRole != null) admin.setRoles(Set.of(adminRole));
        userRepo.save(admin);
        log.warn("=== 已创建默认管理员 '{}' (初始口令见配置 watchpost.bootstrap.admin-password), 请首次登录后立即修改 ===",
                adminUsername);
    }

    /** 同步内置采集模板到 DB (yaml_spec)。 */
    private void syncTemplates() {
        Map<String, CollectTemplate> all = templateLoader.all();
        for (Map.Entry<String, CollectTemplate> e : all.entrySet()) {
            AppType appType;
            try { appType = AppType.of(e.getKey()); }
            catch (Exception ex) { continue; }
            CollectTemplate tpl = e.getValue();
            MonitorTemplateEntity entity = templateRepo.findByAppType(appType).orElseGet(MonitorTemplateEntity::new);
            entity.setAppType(appType);
            entity.setProtocol(tpl.getProtocol());
            entity.setName(appType.name() + " 内置模板");
            // yaml_spec 存原始 YAML (从 classpath 重新读取文本)
            entity.setYamlSpec(readYamlText(appType));
            entity.setBuiltin(true);
            entity.setVersion(entity.getVersion() == null ? 1 : entity.getVersion());
            templateRepo.save(entity);
        }
        log.info("synced {} builtin collect templates to DB", all.size());
    }

    private String readYamlText(AppType appType) {
        try (var in = getClass().getClassLoader().getResourceAsStream("templates/" + appType.name() + ".yaml")) {
            if (in == null) return "";
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("read template yaml failed for {}: {}", appType, e.getMessage());
            return "";
        }
    }
}
