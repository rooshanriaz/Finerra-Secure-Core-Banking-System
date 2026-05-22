package com.fyp.fraud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "fraud")
public class FraudProperties {

    private Thresholds thresholds = new Thresholds();

    @Data
    public static class Thresholds {
        private double block = 0.85;
        private double flag = 0.60;
        private double low = 0.30;
    }
}
