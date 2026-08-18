package com.company.company_clean_hub_be.cccd.service.impl;

import com.company.company_clean_hub_be.cccd.config.CccdValidationProperties;
import com.company.company_clean_hub_be.cccd.enums.DocumentSide;
import com.company.company_clean_hub_be.cccd.opencv.OpenCvImageProcessor;
import com.company.company_clean_hub_be.cccd.template.CccdTemplate;
import com.company.company_clean_hub_be.cccd.template.CccdTemplateRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_features2d;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_features2d.ORB;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * So sánh ảnh CCCD với template để:
 * 1. Nhận diện FRONT / BACK
 * 2. Tính templateScore (0-100)
 * 3. Kiểm tra các required regions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateMatchingServiceImpl {

    private final CccdTemplateRegistry templateRegistry;
    private final OpenCvImageProcessor imageProcessor;
    private final CccdValidationProperties props;
    private final ResourceLoader resourceLoader;

    public static class MatchResult {
        public DocumentSide detectedSide = DocumentSide.UNKNOWN;
        public int templateScore = 0;          // 0-100
        public int regionScore = 0;            // 0-100
        public String matchedTemplateId = null;
    }

    /**
     * So sánh ảnh đã normalize với tất cả templates đang active.
     * Trả về side + score tốt nhất.
     */
    public MatchResult matchAndClassify(Mat normalizedImage) {
        MatchResult best = new MatchResult();

        for (CccdTemplate template : templateRegistry.getAllActive()) {
            Mat templateMat = loadTemplateMat(template.getImagePath());
            if (templateMat == null) continue;

            int score = computeOrbMatchScore(normalizedImage, templateMat);
            if (score > best.templateScore) {
                best.templateScore = score;
                best.detectedSide = template.getSide();
                best.matchedTemplateId = template.getTemplateId();
                best.regionScore = checkRequiredRegions(normalizedImage, template.getRequiredRegions());
            }
        }

        if (best.templateScore < (int) (props.getTemplate().getMatchThreshold() * 100)) {
            best.detectedSide = DocumentSide.UNKNOWN;
        }

        log.debug("[CCCD] Template match: side={} score={}", best.detectedSide, best.templateScore);
        return best;
    }

    // ─── ORB Feature Matching ─────────────────────────────────────────────────

    private int computeOrbMatchScore(Mat input, Mat templateMat) {
        try {
            Mat resized = imageProcessor.resize(input, templateMat.cols(), templateMat.rows());
            Mat grayInput = imageProcessor.toGrayscale(resized);
            Mat grayTemplate = imageProcessor.toGrayscale(templateMat);

            ORB orb = ORB.create(500, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20);
            KeyPointVector kp1 = new KeyPointVector();
            KeyPointVector kp2 = new KeyPointVector();
            Mat desc1 = new Mat();
            Mat desc2 = new Mat();

            orb.detectAndCompute(grayInput, new Mat(), kp1, desc1);
            orb.detectAndCompute(grayTemplate, new Mat(), kp2, desc2);

            if (desc1.empty() || desc2.empty()) return 0;

            DMatchVector matches = new DMatchVector();
            org.bytedeco.opencv.opencv_features2d.BFMatcher matcher =
                    org.bytedeco.opencv.opencv_features2d.BFMatcher.create(
                            opencv_core.NORM_HAMMING, true);
            matcher.match(desc1, desc2, matches);

            // Lọc matches tốt (distance < 60)
            long goodMatches = 0;
            for (long i = 0; i < matches.size(); i++) {
                if (matches.get(i).distance() < 60) goodMatches++;
            }

            long total = Math.max(kp1.size(), kp2.size());
            if (total == 0) return 0;

            // Score 0-100
            return (int) Math.min(100, (goodMatches * 100.0 / Math.min(total, 200)));
        } catch (Exception e) {
            log.warn("[CCCD] ORB matching error: {}", e.getMessage());
            return 0;
        }
    }

    // ─── Required Region Check ────────────────────────────────────────────────

    /**
     * Kiểm tra các vùng đặc trưng có tồn tại (có edge/texture) trong ảnh.
     * Dùng tọa độ tương đối [0.0-1.0].
     */
    private int checkRequiredRegions(Mat image, List<CccdTemplate.RegionSpec> regions) {
        if (regions == null || regions.isEmpty()) return 100;

        int passed = 0;
        int total = regions.size();

        for (CccdTemplate.RegionSpec region : regions) {
            int x = (int) (region.getX() * image.cols());
            int y = (int) (region.getY() * image.rows());
            int w = (int) (region.getWidth() * image.cols());
            int h = (int) (region.getHeight() * image.rows());

            // Clamp để tránh out-of-bounds
            x = Math.max(0, Math.min(x, image.cols() - 1));
            y = Math.max(0, Math.min(y, image.rows() - 1));
            w = Math.min(w, image.cols() - x);
            h = Math.min(h, image.rows() - y);

            if (w <= 0 || h <= 0) continue;

            // Crop vùng
            Rect roi = new Rect(x, y, w, h);
            Mat regionMat = new Mat(image, roi);

            // Kiểm tra vùng có nội dung (variance > threshold)
            double variance = imageProcessor.computeBlurVariance(regionMat);
            if (variance > 10.0) passed++; // vùng có nội dung

            log.trace("[CCCD] Region '{}': variance={}", region.getName(), variance);
        }

        return total > 0 ? (passed * 100 / total) : 0;
    }

    private Mat loadTemplateMat(String path) {
        try {
            byte[] bytes = resourceLoader.getResource(path).getInputStream().readAllBytes();
            Mat buf = new Mat(1, bytes.length, opencv_core.CV_8UC1);
            buf.data().put(bytes);
            return opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
        } catch (IOException e) {
            log.error("[CCCD] Cannot load template: {} - {}", path, e.getMessage());
            return null;
        }
    }
}
