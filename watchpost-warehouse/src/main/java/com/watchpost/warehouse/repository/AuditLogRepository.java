package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    Page<AuditLogEntity> findByOrderByCreateTimeDesc(Pageable pageable);
    Page<AuditLogEntity> findByActionOrderByCreateTimeDesc(String action, Pageable pageable);
    Page<AuditLogEntity> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);
}
