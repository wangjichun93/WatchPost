package com.watchpost.common.spi;

import com.watchpost.common.model.MetricValue;

import java.util.List;
import java.util.Map;

/**
 * 实时值存储 SPI。承载每实例每字段最新值 + 健康态 + 连续失败计数 + 收敛滑动窗口计数。
 * 默认 Redis 实现; 单机降级可用内存 Map。支撑大盘/详情页 P95<1.5s 秒级读。
 */
public interface RealtimeStore {

    /** 写入一次采集的最新值快照 (覆盖)。 */
    void putLatest(long monitorId, List<MetricValue> values, long timestamp, boolean success, int connFailCount);

    /** 读取某实例全部字段最新值 (key: group#field)。 */
    Map<String, Latest> getLatest(long monitorId);

    /** 读取单字段最新值。 */
    Latest getLatest(long monitorId, String group, String field);

    /** 更新实例健康态 (0停用 1正常 2不可达)。 */
    void updateHealth(long monitorId, int status, int connFailCount);

    /** 连续命中计数自增 (收敛: 连续 N 次), 返回自增后的值。窗口过期自动清零。 */
    long incrHit(String fingerprint, int windowSec);

    /** 重置连续命中计数 (恢复正常时)。 */
    void resetHit(String fingerprint);

    /** 抑制窗口: 标记某指纹在 suppressSec 内已通知, 返回是否允许再次通知。 */
    boolean acquireSuppress(String fingerprint, int suppressSec);

    /** 最新值快照。labels 用于区分同字段多维度 (如每磁盘分区/每表空间)。 */
    record Latest(String group, String field, Double doubleValue, String strValue,
                  String unit, long timestamp, boolean success, Map<String, String> labels) {
        /** 存储键: group#field 或 group#field#k=v,k=v (含标签时)。 */
        public String key() {
            if (labels == null || labels.isEmpty()) return group + "#" + field;
            String lk = labels.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "," + b).orElse("");
            return group + "#" + field + "#" + lk;
        }
    }
}
