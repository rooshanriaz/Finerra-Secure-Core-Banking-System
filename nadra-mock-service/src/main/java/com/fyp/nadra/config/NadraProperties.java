package com.fyp.nadra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "nadra.mock")
public class NadraProperties {
    private int minDelayMs = 500;
    private int maxDelayMs = 2000;
    private List<String> passPrefixes = List.of("35201", "35202", "61101");
    private List<String> failPrefixes = List.of("99999", "00000");
    private double biometricThreshold = 0.75;
}
