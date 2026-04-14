package com.k8s.platform.exception;

import lombok.Getter;

@Getter
public class PermissionDeniedException extends RuntimeException {

    private final String username;
    private final String clusterUid;
    private final String namespace;
    private final String resourceKind;
    private final String resourceName;
    private final String action;

    public PermissionDeniedException(String username, String clusterUid, String namespace,
            String resourceKind, String resourceName, String action) {
        super(String.format("Permission denied: user=%s, cluster=%s, namespace=%s, kind=%s, name=%s, action=%s",
                username, clusterUid, namespace, resourceKind, resourceName, action));
        this.username = username;
        this.clusterUid = clusterUid;
        this.namespace = namespace;
        this.resourceKind = resourceKind;
        this.resourceName = resourceName;
        this.action = action;
    }
}
