package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.NotifyPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotifyPolicyRepository extends JpaRepository<NotifyPolicyEntity, Long> {
    List<NotifyPolicyEntity> findByEnabledTrueOrderByPriorityDesc();
}
