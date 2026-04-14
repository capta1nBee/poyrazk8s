package com.k8s.platform.service;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SeverityScoreCalculator {

    private static final Map<String, Double> WEIGHTS = Map.of(
            "CRITICAL", 9.0,
            "HIGH", 7.0,
            "MEDIUM", 4.0,
            "LOW", 1.0);

    public double calculate(
            int critical, int high, int medium, int low) {

        return critical * WEIGHTS.get("CRITICAL")
                + high * WEIGHTS.get("HIGH")
                + medium * WEIGHTS.get("MEDIUM")
                + low * WEIGHTS.get("LOW");
    }
}
