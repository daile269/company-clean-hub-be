package com.company.company_clean_hub_be.cccd.service.impl;

import com.company.company_clean_hub_be.cccd.config.CccdValidationProperties;
import com.company.company_clean_hub_be.cccd.dto.ImageQualityResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Tính điểm tổng hợp (weighted score) từ các bước kiểm tra.
 */
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl {

    private final CccdValidationProperties props;

    public static class ScoreInput {
        public int qualityScore     = 0;   // 0-100
        public int cardDetected     = 0;   // 0 hoặc 100
        public int aspectRatioScore = 0;   // 0 hoặc 100
        public int completenessScore= 0;   // 0 hoặc 100
        public int templateScore    = 0;   // 0-100
        public int regionScore      = 0;   // 0-100
    }

    /**
     * Tính weighted score tổng hợp (0-100).
     */
    public int compute(ScoreInput input) {
        var w = props.getScore().getWeight();

        double score =
                (input.qualityScore      * w.getQuality())      / 100.0 +
                (input.cardDetected      * w.getCardDetection()) / 100.0 +
                (input.aspectRatioScore  * w.getAspectRatio())   / 100.0 +
                (input.completenessScore * w.getCompleteness())  / 100.0 +
                (input.templateScore     * w.getTemplateMatch()) / 100.0 +
                (input.regionScore       * w.getRequiredRegions()) / 100.0;

        return (int) Math.round(score);
    }

    /**
     * Tính quality score từ ImageQualityResult chi tiết.
     */
    public int buildQualityScore(ImageQualityResult quality) {
        return quality.getQualityScore();
    }
}
