package com.watchpost.warehouse.entity;

import com.watchpost.common.enums.RoleCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 角色 (RBAC 三角色)。对应 wp_role。 */
@Getter
@Setter
@Entity
@Table(name = "wp_role")
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleCode code;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(length = 128)
    private String description;
}
