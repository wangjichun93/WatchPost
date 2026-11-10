package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.MetricFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetricFieldRepository extends JpaRepository<MetricFieldEntity, Long> {
    List<MetricFieldEntity> findByMonitorIdOrderBySortNoAsc(Long monitorId);
    Optional<MetricFieldEntity> findByMonitorIdAndMetricGroupAndFieldName(Long monitorId, String group, String field);
    void deleteByMonitorId(Long monitorId);
}
