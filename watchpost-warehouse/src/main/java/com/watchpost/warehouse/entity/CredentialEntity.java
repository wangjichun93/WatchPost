package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.ProtocolType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 采集凭据密文 (AES-256-GCM)。对应 wp_credential。运行时解密注入采集器, 用后即弃, 页面/日志脱敏。 */
@Getter
@Setter
@Entity
@Table(name = "wp_credential",
        uniqueConstraints = @UniqueConstraint(name = "uk_cred", columnNames = {"monitor_id", "cred_type"}))
public class CredentialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private Long monitorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cred_type", nullable = false)
    private ProtocolType credType;

    @Column(length = 128)
    private String username;

    @Column(name = "cipher_text", nullable = false, columnDefinition = "text")
    private String cipherText;

    @Column(name = "kms_key_id", length = 64)
    private String kmsKeyId;

    @Column(name = "extra_json", columnDefinition = "jsonb")
    private String extraJson;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();
}
