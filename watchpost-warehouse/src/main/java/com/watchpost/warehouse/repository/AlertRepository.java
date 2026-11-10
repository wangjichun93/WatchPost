package com.watchpost.warehouse.repository;

import com.watchpost.common.enums.AlertState;
import com.watchpost.warehouse.entity.AlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 告警仓库。
 * 动态搜索用 JpaSpecificationExecutor (同 MonitorRepository 原因: stringtype=unspecified 下
 * `:param IS NULL` 的 untyped-null 参数在 PG 会报 "could not determine data type")。
 */
public interface AlertRepository extends JpaRepository<AlertEntity, Long>,
        JpaSpecificationExecutor<AlertEntity> {

    Optional<AlertEntity> findByAlertNo(String alertNo);

    /** 活动告警按指纹查找 (收敛: 同实例同指标合并为一条)。 */
    Optional<AlertEntity> findByFingerprintAndStateIn(String fingerprint, List<AlertState> states);

    List<AlertEntity> findByMonitorIdOrderByLastTriggerAtDesc(Long monitorId);

    long countByStateIn(List<AlertState> states);

    long countBySeverityAndStateIn(com.watchpost.common.enums.Severity severity, List<AlertState> states);

    /** 值班视图: 未确认活动告警, 按级别降序常驻。 */
    List<AlertEntity> findByStateInOrderBySeverityDescLastTriggerAtDesc(List<AlertState> states);

    /** 历史告警清理 (默认保留30天)。 */
    void deleteByStateAndClosedAtBefore(AlertState state, OffsetDateTime before);
}
