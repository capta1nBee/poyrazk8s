package com.k8s.platform.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ImageScanLockManager {

    private final Set<String> activeScans = ConcurrentHashMap.newKeySet();

    public boolean acquire(String clusterId, String image) {
        return activeScans.add(clusterId + "|" + image);
    }

    public void release(String clusterId, String image) {
        activeScans.remove(clusterId + "|" + image);
    }
}
