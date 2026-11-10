package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.CredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<CredentialEntity, Long> {
    Optional<CredentialEntity> findByMonitorId(Long monitorId);
    void deleteByMonitorId(Long monitorId);
}
