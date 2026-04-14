package com.k8s.platform.domain.entity.federation;

import com.k8s.platform.domain.entity.Cluster;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "federation_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FederationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "federation_id", nullable = false)
    private Federation federation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_cluster_id", nullable = false)
    private Cluster memberCluster;
}
