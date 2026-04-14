package com.k8s.platform.domain.entity.casbin;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "casbin_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CasbinRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String ptype;  // 'p' or 'g'

    @Column(length = 512) private String v0;
    @Column(length = 512) private String v1;
    @Column(length = 512) private String v2;
    @Column(length = 512) private String v3;
    @Column(length = 512) private String v4;
    @Column(length = 512) private String v5;
}
