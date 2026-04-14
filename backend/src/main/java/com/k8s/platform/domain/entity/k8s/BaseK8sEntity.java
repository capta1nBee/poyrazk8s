package com.k8s.platform.domain.entity.k8s;

public interface BaseK8sEntity {
    void setName(String name);

    void setUid(String uid);

    void setLabels(String labels);

    void setAnnotations(String annotations);

    void setResourceVersion(String resourceVersion);

    void setK8sCreatedAt(String k8sCreatedAt);

    void setApiVersion(String apiVersion);

    void setClusterId(Long clusterId);

    void setNamespace(String namespace);

    void setGeneration(Integer generation);

    void setOwnerRefs(String ownerRefs);

    void setManagedFields(String managedFields);

    void setIsDeleted(Boolean isDeleted);

    void setUpdatedAt(java.time.LocalDateTime updatedAt);
}
