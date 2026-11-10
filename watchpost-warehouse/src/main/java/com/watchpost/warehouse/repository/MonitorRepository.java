package com.watchpost.warehouse.repository;

import com.watchpost.common.enums.AppType;
import com.watchpost.warehouse.entity.MonitorEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 监控对象仓库。
 * 动态搜索用 JpaSpecificationExecutor (Criteria API) 而非 @Query:
 * 因 JDBC url 设 stringtype=unspecified (原生 enum/jsonb 列写入所需), String 参数为 unknown 类型,
 * 在 `lower(concat('%',:kw,'%'))` 中会被 PG 解析为 bytea 报错; Specification 在 Java 侧条件化构造谓词,
 * null 过滤直接省略 (无 untyped-null 参数), LIKE 模式在 Java 拼好 (无 concat), 彻底规避该问题。
 */
public interface MonitorRepository extends JpaRepository<MonitorEntity, Long>,
        JpaSpecificationExecutor<MonitorEntity> {

    List<MonitorEntity> findByDeletedFalseAndEnabledTrue();

    Optional<MonitorEntity> findByIdAndDeletedFalse(Long id);

    Page<MonitorEntity> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();

    long countByDeletedFalseAndStatus(Integer status);

    List<MonitorEntity> findByDeletedFalseAndAppType(AppType appType);
}
