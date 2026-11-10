package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.CollectorNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollectorNodeRepository extends JpaRepository<CollectorNodeEntity, Long> {
    Optional<CollectorNodeEntity> findByIdentity(String identity);
}
