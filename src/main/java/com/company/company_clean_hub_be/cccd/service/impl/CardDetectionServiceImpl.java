package com.company.company_clean_hub_be.cccd.service.impl;

import com.company.company_clean_hub_be.cccd.config.CccdValidationProperties;
import com.company.company_clean_hub_be.cccd.opencv.ContourDetector;
import com.company.company_clean_hub_be.cccd.opencv.PerspectiveTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point2f;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kết hợp ContourDetector + PerspectiveTransformer để:
 * 1. Phát hiện thẻ CCCD (tứ giác lớn nhất)
 * 2. Chỉnh góc nghiêng (perspective correction)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardDetectionServiceImpl {

    private final ContourDetector contourDetector;
    private final PerspectiveTransformer perspectiveTransformer;
    private final CccdValidationProperties props;

    public static class CardDetectionResult {
        public boolean cardDetected;
        public boolean cardCropped;
        public double aspectRatio = -1;
        public Mat normalizedImage;      // sau perspective correction
        public List<Point2f> corners;
    }

    public CardDetectionResult detect(Mat image) {
        CardDetectionResult result = new CardDetectionResult();

        double minAreaRatio = props.getCard().getMinAreaRatio();
        List<Point2f> corners = contourDetector.detectCardContour(image, minAreaRatio);

        boolean isFallback = false;
        if (corners.isEmpty()) {
            // Fallback: nếu ảnh đã được crop sẵn sát mép thẻ (aspect ratio gần 1.58)
            int w = image.cols();
            int h = image.rows();
            double imgRatio = (double) Math.max(w, h) / Math.min(w, h);
            if (imgRatio >= 1.20 && imgRatio <= 2.10) {
                log.info("[CCCD] Contour not found, but image aspect ratio ({}) matches card frame. Fallback to full frame.", imgRatio);
                corners = contourDetector.getFullFrameCorners(w, h);
                isFallback = true;
            } else {
                result.cardDetected = false;
                log.debug("[CCCD] Card not detected");
                return result;
            }
        }

        result.cardDetected = true;
        result.corners = corners;
        double computedRatio = contourDetector.computeAspectRatioFromCorners(corners);
        if (computedRatio < 1.0 && computedRatio > 0) {
            computedRatio = 1.0 / computedRatio; // Chuẩn hóa về chiều rộng / chiều cao > 1.0 cho ảnh chụp dọc
        }
        result.aspectRatio = computedRatio;

        // Perspective correction → normalize về kích thước chuẩn
        result.normalizedImage = perspectiveTransformer.correctPerspective(image, corners);

        return result;
    }

    public boolean isAspectRatioValid(double ratio) {
        if (ratio < 0) return false;
        double min = props.getCard().getAspectRatioMin();
        double max = props.getCard().getAspectRatioMax();
        return ratio >= min && ratio <= max;
    }
}
