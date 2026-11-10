package com.watchpost.warehouse.repository;

import com.watchpost.common.enums.AppType;
import com.watchpost.warehouse.entity.MonitorTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonitorTemplateRepository extends JpaRepository<MonitorTemplateEntity, Long> {
    Optional<MonitorTemplateEntity> findByAppType(AppType appType);
}
