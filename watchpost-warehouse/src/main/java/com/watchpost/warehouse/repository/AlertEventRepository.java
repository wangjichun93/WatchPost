package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.AlertEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEventEntity, Long> {
    List<AlertEventEntity> findByAlertIdOrderByCreateTimeAsc(Long alertId);
}
