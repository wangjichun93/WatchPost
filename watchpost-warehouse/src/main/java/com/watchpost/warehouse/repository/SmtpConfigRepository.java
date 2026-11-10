package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.SmtpConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SmtpConfigRepository extends JpaRepository<SmtpConfigEntity, Long> {
    Optional<SmtpConfigEntity> findFirstByEnabledTrueOrderByIdDesc();
}
