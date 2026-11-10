package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** SMTP 配置 (单条生效, 授权码加密)。对应 wp_smtp_config。 */
@Getter
@Setter
@Entity
@Table(name = "wp_smtp_config")
public class SmtpConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String host;

    @Column(nullable = false)
    private Integer port = 465;

    @Column(nullable = false)
    private Boolean starttls = true;

    @Column(nullable = false, length = 128)
    private String sender;

    @Column(name = "auth_cipher", nullable = false, columnDefinition = "text")
    private String authCipher;

    @Column(name = "kms_key_id", length = 64)
    private String kmsKeyId;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "create_time", nullable = false)
    private OffsetDateTime createTime = OffsetDateTime.now();

    @Column(name = "update_time", nullable = false)
    private OffsetDateTime updateTime = OffsetDateTime.now();
}
