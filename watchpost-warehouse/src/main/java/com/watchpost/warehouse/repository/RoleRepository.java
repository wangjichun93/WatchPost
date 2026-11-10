package com.watchpost.warehouse.repository;

import com.watchpost.common.enums.RoleCode;
import com.watchpost.warehouse.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByCode(RoleCode code);
}
