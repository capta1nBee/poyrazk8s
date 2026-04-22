package com.k8s.platform.service;

/**
 * Parsed Docker/OCI image reference.
 *
 * @param raw      original input string (e.g. "docker.io/library/nginx:1.25")
 * @param registry resolved registry host (e.g. "docker.io", "harbor.corp.com")
 * @param repo     repository path without registry (e.g. "library/nginx")
 * @param tag      image tag or null (e.g. "1.25")
 * @param digest   image digest or null (e.g. "sha256:abc123...")
 */
public record ImageRef(
        String raw,
        String registry,
        String repo,
        String tag,
        String digest
) {
}
