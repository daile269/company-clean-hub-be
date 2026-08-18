package com.company.company_clean_hub_be.cccd.service.impl;

import com.company.company_clean_hub_be.cccd.config.CccdValidationProperties;
import com.company.company_clean_hub_be.cccd.dto.ImageQualityResult;
import com.company.company_clean_hub_be.cccd.enums.ValidationErrorCode;
import com.company.company_clean_hub_be.cccd.opencv.OpenCvImageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageQualityServiceImpl {

    private final CccdValidationProperties props;
    private final OpenCvImageProcessor processor;

    /**
     * Validate file đầu vào (MIME, size, decode) rồi kiểm tra chất lượng ảnh.
     */
    public ImageQualityResult evaluate(MultipartFile file, Mat image) {
        List<ValidationErrorCode> errors = new ArrayList<>();
        int score = 100;

        // Resolution (support both landscape & portrait orientation)
        int w = image.cols();
        int h = image.rows();
        int maxSide = Math.max(w, h);
        int minSide = Math.min(w, h);
        boolean tooSmall = maxSide < props.getMinWidth() || minSide < props.getMinHeight();
        if (tooSmall) {
            errors.add(ValidationErrorCode.IMAGE_TOO_SMALL);
            score -= 30;
        }

        // Blur
        double blurVariance = processor.computeBlurVariance(image);
        boolean blurry = blurVariance < props.getBlurThreshold();
        if (blurry) {
            score -= 20;
            if (blurVariance < 20.0) {
                errors.add(ValidationErrorCode.IMAGE_TOO_BLURRY);
            }
        }

        // Brightness
        double brightness = processor.computeBrightness(image);
        boolean tooDark = brightness < props.getMinBrightness();
        boolean tooBright = brightness > props.getMaxBrightness();
        if (tooDark) {
            errors.add(ValidationErrorCode.IMAGE_TOO_DARK);
            score -= 25;
        }
        if (tooBright) {
            errors.add(ValidationErrorCode.IMAGE_TOO_BRIGHT);
            score -= 20;
        }

        // Contrast
        double contrast = processor.computeContrast(image);
        boolean lowContrast = contrast < props.getMinContrast();
        if (lowContrast) {
            errors.add(ValidationErrorCode.LOW_CONTRAST);
            score -= 15;
        }

        int finalScore = Math.max(0, score);
        boolean passed = errors.isEmpty();

        return ImageQualityResult.builder()
                .passed(passed)
                .qualityScore(finalScore)
                .blurry(blurry)
                .tooDark(tooDark)
                .tooBright(tooBright)
                .lowContrast(lowContrast)
                .tooSmall(tooSmall)
                .width(w)
                .height(h)
                .blurVariance(blurVariance)
                .brightness(brightness)
                .contrast(contrast)
                .errors(errors)
                .build();
    }

    /**
     * Validate file trước khi decode.
     * @return null nếu OK, ValidationErrorCode nếu fail
     */
    public ValidationErrorCode validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return ValidationErrorCode.IMAGE_EMPTY;

        long maxBytes = (long) props.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) return ValidationErrorCode.FILE_TOO_LARGE;

        String contentType = file.getContentType();
        if (contentType == null || !props.getAllowedMimeTypes().contains(contentType)) {
            return ValidationErrorCode.INVALID_FILE_TYPE;
        }
        return null;
    }
}
