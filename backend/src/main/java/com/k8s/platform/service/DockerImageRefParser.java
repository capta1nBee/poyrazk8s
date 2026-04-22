package com.k8s.platform.service;

/**
 * Parses a Docker/OCI image reference string into its components:
 * registry, repository, tag, and digest.
 *
 * <p>Follows Docker's registry detection convention:
 * the first path segment is treated as a registry if it contains '.' or ':',
 * or equals "localhost". Otherwise, it defaults to "docker.io".
 */
public final class DockerImageRefParser {

    private DockerImageRefParser() {
    }

    /**
     * Parse a raw image string into an {@link ImageRef}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code nginx} → registry=docker.io, repo=library/nginx</li>
     *   <li>{@code myuser/myapp:v2} → registry=docker.io, repo=myuser/myapp, tag=v2</li>
     *   <li>{@code harbor.corp.com/team/app:latest} → registry=harbor.corp.com, repo=team/app, tag=latest</li>
     *   <li>{@code gcr.io/project/img@sha256:abc...} → registry=gcr.io, repo=project/img, digest=sha256:abc...</li>
     * </ul>
     */
    public static ImageRef parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Image reference cannot be null or blank");
        }

        String image = input.trim();

        // ── 1. Digest extraction ────────────────────────────────────
        String digest = null;
        int atIndex = image.indexOf('@');
        if (atIndex > -1) {
            digest = image.substring(atIndex + 1);
            image = image.substring(0, atIndex);
        }

        // ── 2. Tag extraction (only last ':' after last '/') ────────
        String tag = null;
        int lastSlash = image.lastIndexOf('/');
        int lastColon = image.lastIndexOf(':');
        if (lastColon > lastSlash) {
            tag = image.substring(lastColon + 1);
            image = image.substring(0, lastColon);
        }

        // ── 3. Registry detection ───────────────────────────────────
        // Docker rule: first segment is a registry if it contains '.' or ':',
        // or equals "localhost".
        String registry;
        String repo;

        String[] parts = image.split("/", 2);
        if (parts.length == 1) {
            // No slash → Docker Hub short form (e.g. "nginx")
            registry = "docker.io";
            repo = "library/" + parts[0];
        } else {
            String possibleRegistry = parts[0];
            boolean isRegistry = possibleRegistry.contains(".")
                    || possibleRegistry.contains(":")
                    || possibleRegistry.equals("localhost");

            if (isRegistry) {
                registry = possibleRegistry;
                repo = parts[1];
            } else {
                // e.g. "myuser/myapp" → Docker Hub
                registry = "docker.io";
                repo = image;
            }
        }

        return new ImageRef(input, registry, repo, tag, digest);
    }
}
