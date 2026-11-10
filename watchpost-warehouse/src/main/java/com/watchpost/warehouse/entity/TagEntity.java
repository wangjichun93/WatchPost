package com.watchpost.warehouse.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 标签 (键值对, 用于告警路由)。对应 wp_tag。 */
@Getter
@Setter
@Entity
@Table(name = "wp_tag", uniqueConstraints = @UniqueConstraint(name = "uk_tag", columnNames = {"tag_key", "tag_value"}))
public class TagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tag_key", nullable = false, length = 64)
    private String tagKey;

    @Column(name = "tag_value", nullable = false, length = 64)
    private String tagValue;

    public String asPair() { return tagKey + ":" + tagValue; }
}
