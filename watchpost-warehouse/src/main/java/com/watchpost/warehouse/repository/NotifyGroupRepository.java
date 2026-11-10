package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.NotifyGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotifyGroupRepository extends JpaRepository<NotifyGroupEntity, Long> {
    Optional<NotifyGroupEntity> findByName(String name);
    List<NotifyGroupEntity> findByEnabledTrue();
}
