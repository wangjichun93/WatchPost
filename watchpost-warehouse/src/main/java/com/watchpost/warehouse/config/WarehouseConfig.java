package com.watchpost.warehouse.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 存储层装配: JPA 实体扫描 + 仓库扫描。
 * Flyway 迁移脚本位于本模块 classpath:db/migration (V1__init_schema, V2__seed_data), 由 Spring Boot 自动执行。
 * Redis (StringRedisTemplate) 与数据源由 Spring Boot 自动配置。
 */
@Configuration
@EntityScan(basePackages = "com.watchpost.warehouse.entity")
@EnableJpaRepositories(basePackages = "com.watchpost.warehouse.repository")
public class WarehouseConfig {
}
