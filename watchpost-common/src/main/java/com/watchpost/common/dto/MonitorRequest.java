package com.watchpost.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 新增/编辑监控对象请求 (PRD: 全程 Web 表单, ≤2min 接入)。
 * 凭据以明文提交, 服务端 AES-256-GCM 加密后落 wp_credential; 响应一律脱敏。
 */
@Data
public class MonitorRequest {
    private Long id;                 // 编辑时携带
    @NotBlank(message = "监控名称不能为空")
    private String name;
    @NotBlank(message = "监控类型不能为空")
    private String appType;          // linux/http/mysql/postgresql/oracle
    @NotBlank(message = "主机地址不能为空")
    private String host;
    private Integer port;
    @Min(value = 15, message = "采集周期不得小于15秒")
    @Max(value = 3600, message = "采集周期不得大于3600秒")
    private Integer intervalSec = 30;
    private String remark;
    private List<String> tags;       // ["env:prod", "team:db"]

    /** 凭据 (按协议): ssh{username,password|privateKey} / jdbc{username,password} / http{token} / snmp{community} / ipmi{username,password} */
    @NotNull(message = "凭据不能为空")
    private Map<String, String> credential;

    /** HTTP 型额外参数 (endpoint/method 等), 其他类型可空 */
    private Map<String, Object> extra;
}
