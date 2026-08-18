package com.company.company_clean_hub_be.cccd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "cccd.validation")
public class CccdValidationProperties {

    private int maxFileSizeMb = 10;
    private String allowedMimeTypes = "image/jpeg,image/png,image/webp";

    // Image quality
    private int minWidth = 600;
    private int minHeight = 400;
    private double blurThreshold = 100.0;
    private int minBrightness = 40;
    private int maxBrightness = 230;
    private int minContrast = 30;

    // Card detection
    private Card card = new Card();

    // Scoring
    private Score score = new Score();

    // Template
    private Template template = new Template();

    @Data
    public static class Card {
        private double minAreaRatio = 0.15;
        private double aspectRatioMin = 1.4;
        private double aspectRatioMax = 1.7;
        private double aspectRatioTolerance = 0.15;
    }

    @Data
    public static class Score {
        private int accept = 80;
        private int review = 65;
        private Weight weight = new Weight();

        @Data
        public static class Weight {
            private int quality = 20;
            private int cardDetection = 20;
            private int aspectRatio = 10;
            private int completeness = 10;
            private int templateMatch = 25;
            private int requiredRegions = 15;
        }
    }

    @Data
    public static class Template {
        private String basePath = "classpath:static/cccd-templates";
        private String frontV1 = "front/cccd_front_v1.png";
        private String backV1 = "back/cccd_back_v1.png";
        private double matchThreshold = 0.65;
    }
}
