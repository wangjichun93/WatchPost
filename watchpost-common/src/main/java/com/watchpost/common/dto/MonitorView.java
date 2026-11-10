package com.watchpost.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** 监控对象视图 (响应 DTO, 凭据一律脱敏)。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorView {
    private Long id;
    private String name;
    private String appType;
    private String protocol;
    private String host;
    private Integer port;
    private Integer intervalSec;
    private Integer status;          // 0停用 1正常 2不可达
    private String statusLabel;
    private Integer connFailCount;
    private Long lastCollectAt;      // epoch millis
    private String remark;
    private List<String> tags;
    /** 脱敏后的凭据摘要 (仅用户名/类型, 不含密钥) */
    private Map<String, String> credentialMasked;
    /** 扩展配置 (HTTP 型: method/path/headers/body/expectStatus) */
    private Map<String, Object> extra;
    private Long createTime;
}
