package com.watchpost.warehouse.repository;

import com.watchpost.common.enums.RuleScope;
import com.watchpost.warehouse.entity.ThresholdRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThresholdRuleRepository extends JpaRepository<ThresholdRuleEntity, Long> {

    /** 全局默认模板规则 (按 app_type)。 */
    List<ThresholdRuleEntity> findByScopeAndScopeRefAndEnabledTrue(RuleScope scope, String scopeRef);

    /** 某实例的覆写规则。 */
    List<ThresholdRuleEntity> findByMonitorIdAndEnabledTrue(Long monitorId);

    /** 某字段绑定的规则。 */
    List<ThresholdRuleEntity> findByMetricFieldIdAndEnabledTrue(Long metricFieldId);

    List<ThresholdRuleEntity> findByEnabledTrue();
}
