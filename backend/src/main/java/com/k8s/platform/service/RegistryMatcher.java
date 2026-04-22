package com.k8s.platform.service;

import com.k8s.platform.domain.entity.ImageRegistryCredential;

import java.util.List;

/**
 * Matches a parsed {@link ImageRef} against a list of {@link ImageRegistryCredential}
 * using exact registry comparison.
 *
 * <p>This eliminates false-positive matches that occur with substring/prefix approaches.
 * For example, {@code evil-my-registry.com} will never match a credential for {@code my-registry.com}.
 */
public final class RegistryMatcher {

    private RegistryMatcher() {
    }

    /**
     * Find the best matching registry credential for the given image reference.
     * Uses exact registry host comparison. Longest (most specific) match wins.
     *
     * @param ref         parsed image reference
     * @param credentials list of registry credentials to match against
     * @return the best matching credential, or null if no match
     */
    public static ImageRegistryCredential match(ImageRef ref, List<ImageRegistryCredential> credentials) {
        if (credentials == null || credentials.isEmpty()) return null;

        String imageRegistry = ref.registry().toLowerCase();

        ImageRegistryCredential best = null;
        int bestScore = -1;

        for (ImageRegistryCredential cred : credentials) {
            String registry = cred.getRegistryUrl().toLowerCase();

            // Exact registry match only
            if (!imageRegistry.equals(registry)) continue;

            int score = registry.length();
            if (score > bestScore) {
                best = cred;
                bestScore = score;
            }
        }

        return best;
    }
}
