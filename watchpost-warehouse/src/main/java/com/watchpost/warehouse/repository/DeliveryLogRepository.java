package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.DeliveryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLogEntity, Long> {
    List<DeliveryLogEntity> findByAlertIdOrderByCreateTimeDesc(Long alertId);
    /** 待重试投递 (status=2失败 且 next_retry_at 到期)。 */
    List<DeliveryLogEntity> findByStatusAndNextRetryAtBefore(Integer status, OffsetDateTime now);
    List<DeliveryLogEntity> findTop100ByOrderByCreateTimeDesc();
}
