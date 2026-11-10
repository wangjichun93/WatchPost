package com.watchpost.warehouse.repository;

import com.watchpost.warehouse.entity.MetricHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MetricHistoryRepository extends JpaRepository<MetricHistoryEntity, MetricHistoryEntity.PK> {

    @Query("SELECT h FROM MetricHistoryEntity h WHERE h.monitorId = :mid " +
            "AND h.metricGroup = :grp AND h.fieldName = :fld " +
            "AND h.recordTime BETWEEN :from AND :to ORDER BY h.recordTime ASC")
    List<MetricHistoryEntity> queryRange(@Param("mid") Long monitorId,
                                         @Param("grp") String group,
                                         @Param("fld") String field,
                                         @Param("from") Long from,
                                         @Param("to") Long to);
}
