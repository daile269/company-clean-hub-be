package com.company.company_clean_hub_be.cccd.service.impl;

import com.company.company_clean_hub_be.cccd.config.CccdValidationProperties;
import com.company.company_clean_hub_be.cccd.dto.CccdSideResult;
import com.company.company_clean_hub_be.cccd.dto.CccdValidationResponse;
import com.company.company_clean_hub_be.cccd.dto.ImageQualityResult;
import com.company.company_clean_hub_be.cccd.enums.DocumentSide;
import com.company.company_clean_hub_be.cccd.enums.ValidationErrorCode;
import com.company.company_clean_hub_be.cccd.enums.ValidationStatus;
import com.company.company_clean_hub_be.cccd.opencv.OpenCvImageProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator: điều phối toàn bộ pipeline kiểm tra CCCD.
 * Kết hợp tất cả sub-services theo đúng thứ tự pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CccdValidationServiceImpl {

    private final ImageQualityServiceImpl imageQualityService;
    private final CardDetectionServiceImpl cardDetectionService;
    private final TemplateMatchingServiceImpl templateMatchingService;
    private final ScoreServiceImpl scoreService;
    private final OpenCvImageProcessor imageProcessor;
    private final com.company.company_clean_hub_be.cccd.opencv.CccdQrScanner cccdQrScanner;
    private final CccdValidationProperties props;

    /**
     * Điểm vào chính: nhận 2 MultipartFile (front + back), trả về kết quả validation.
     */
    public CccdValidationResponse validate(MultipartFile frontFile, MultipartFile backFile) {
        List<ValidationErrorCode> overallErrors = new ArrayList<>();

        // Kiểm tra đủ 2 mặt
        boolean hasFront = frontFile != null && !frontFile.isEmpty();
        boolean hasBack = backFile != null && !backFile.isEmpty();

        if (!hasFront) overallErrors.add(ValidationErrorCode.MISSING_FRONT);
        if (!hasBack) overallErrors.add(ValidationErrorCode.MISSING_BACK);

        if (!hasFront || !hasBack) {
            return CccdValidationResponse.builder()
                    .valid(false)
                    .documentType("CCCD")
                    .status(ValidationStatus.INVALID)
                    .overallScore(0)
                    .errors(overallErrors)
                    .build();
        }

        // Xử lý từng mặt
        CccdSideResult frontResult = processSide(frontFile, DocumentSide.FRONT);
        CccdSideResult backResult  = processSide(backFile,  DocumentSide.BACK);

        // Kiểm tra side có bị swap không
        if (frontResult.getSide() == DocumentSide.BACK && backResult.getSide() == DocumentSide.FRONT) {
            log.info("[CCCD] Front/Back uploaded in reverse order, swapping results");
            CccdSideResult tmp = frontResult;
            frontResult = backResult;
            backResult = tmp;
        }

        // Trích xuất thông tin từ QR Code (thử mặt sau trước, nếu không được thì thử mặt trước)
        com.company.company_clean_hub_be.cccd.dto.CccdExtractedData extractedData = null;
        try {
            byte[] backBytes = backFile.getBytes();
            // 1. Thử quét luồng byte nguyên bản mặt sau
            extractedData = cccdQrScanner.scanQrFromBytes(backBytes);

            // 2. Thử quét trên ảnh mặt sau đã được cắt & cân chỉnh thẻ (Cropped Normalized Mat)
            if (extractedData == null) {
                Mat backMat = imageProcessor.decodeImage(backBytes);
                if (backMat != null && !backMat.empty()) {
                    CardDetectionServiceImpl.CardDetectionResult backDetection = cardDetectionService.detect(backMat);
                    Mat croppedBack = (backDetection.cardDetected && backDetection.normalizedImage != null)
                            ? backDetection.normalizedImage : backMat;
                    extractedData = cccdQrScanner.scanQrCode(croppedBack);
                    backMat.release();
                }
            }

            // 3. Thử mặt trước nếu mặt sau không có QR
            if (extractedData == null) {
                byte[] frontBytes = frontFile.getBytes();
                extractedData = cccdQrScanner.scanQrFromBytes(frontBytes);

                if (extractedData == null) {
                    Mat frontMat = imageProcessor.decodeImage(frontBytes);
                    if (frontMat != null && !frontMat.empty()) {
                        CardDetectionServiceImpl.CardDetectionResult frontDetection = cardDetectionService.detect(frontMat);
                        Mat croppedFront = (frontDetection.cardDetected && frontDetection.normalizedImage != null)
                                ? frontDetection.normalizedImage : frontMat;
                        extractedData = cccdQrScanner.scanQrCode(croppedFront);
                        frontMat.release();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[CCCD] Failed to scan QR Code: {}", e.getMessage());
        }

        // Nếu trích xuất thành công mã QR chuẩn 12 số của Bộ Công An -> Khẳng định 100% là CCCD Việt Nam hợp lệ
        if (extractedData != null && extractedData.getIdCard() != null) {
            log.info("[CCCD] QR extracted successfully for ID: {}. Boosting validation status to VALID.", extractedData.getIdCard());
            if (!frontResult.isValid() || frontResult.getOverallScore() < 70) {
                frontResult = frontResult.toBuilder()
                        .valid(true)
                        .status(ValidationStatus.VALID)
                        .templateScore(Math.max(frontResult.getTemplateScore(), 85))
                        .overallScore(Math.max(frontResult.getOverallScore(), 85))
                        .build();
            }
            if (!backResult.isValid() || backResult.getOverallScore() < 70) {
                backResult = backResult.toBuilder()
                        .valid(true)
                        .status(ValidationStatus.VALID)
                        .templateScore(Math.max(backResult.getTemplateScore(), 85))
                        .overallScore(Math.max(backResult.getOverallScore(), 85))
                        .build();
            }
        }

        // Tổng hợp kết quả
        int overallScore = (frontResult.getOverallScore() + backResult.getOverallScore()) / 2;
        boolean bothValid = frontResult.isValid() && backResult.isValid();
        ValidationStatus status = determineStatus(overallScore, bothValid, frontResult, backResult);

        if (extractedData != null && extractedData.getIdCard() != null) {
            status = ValidationStatus.VALID;
            bothValid = true;
        }

        if (status == ValidationStatus.INVALID || !bothValid) {
            overallErrors.add(ValidationErrorCode.INVALID_DOCUMENT);
        }

        return CccdValidationResponse.builder()
                .valid(bothValid && status != ValidationStatus.INVALID)
                .documentType("CCCD")
                .status(status)
                .front(frontResult)
                .back(backResult)
                .overallScore(overallScore)
                .extractedData(extractedData)
                .errors(overallErrors)
                .build();
    }

    // ─── Process single side ──────────────────────────────────────────────────

    private CccdSideResult processSide(MultipartFile file, DocumentSide expectedSide) {
        List<ValidationErrorCode> errors = new ArrayList<>();

        // [1] File validation
        ValidationErrorCode fileError = imageQualityService.validateFile(file);
        if (fileError != null) {
            return failResult(expectedSide, 0, 0, List.of(fileError));
        }

        // [2] Decode image
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            return failResult(expectedSide, 0, 0, List.of(ValidationErrorCode.IMAGE_DECODE_FAILED));
        }

        // Magic bytes check
        if (!imageProcessor.isValidImageMagicBytes(bytes)) {
            return failResult(expectedSide, 0, 0, List.of(ValidationErrorCode.INVALID_FILE_TYPE));
        }

        Mat image = imageProcessor.decodeImage(bytes);
        if (image == null || image.empty()) {
            return failResult(expectedSide, 0, 0, List.of(ValidationErrorCode.IMAGE_DECODE_FAILED));
        }

        // Tự động xoay ảnh dọc (Portrait) về nằm ngang (Landscape) cho ảnh chụp điện thoại
        if (image.rows() > image.cols()) {
            Mat rotated = new Mat();
            org.bytedeco.opencv.global.opencv_core.rotate(image, rotated, org.bytedeco.opencv.global.opencv_core.ROTATE_90_CLOCKWISE);
            image.release();
            image = rotated;
        }

        try {
            // [3] Image Quality Check
            ImageQualityResult quality = imageQualityService.evaluate(file, image);
            errors.addAll(quality.getErrors());
            int qualityScore = quality.getQualityScore();

            // [4] Card Detection + Perspective Correction
            CardDetectionServiceImpl.CardDetectionResult detection = cardDetectionService.detect(image);
            int cardScore = detection.cardDetected ? 100 : 0;

            if (!detection.cardDetected) {
                errors.add(ValidationErrorCode.CARD_NOT_DETECTED);
            }
            // cardCropped will deduct score via completenessScore instead of blocking as hard error

            // [5] Aspect Ratio (deduct score if outside optimal ratio range, without hard blocking)
            int aspectRatioScore = 0;
            if (detection.cardDetected) {
                boolean ratioOk = cardDetectionService.isAspectRatioValid(detection.aspectRatio);
                aspectRatioScore = ratioOk ? 100 : 60;
            }

            // [6+7] Template Matching + Side Classification
            Mat matForMatch = (detection.normalizedImage != null) ? detection.normalizedImage : image;
            TemplateMatchingServiceImpl.MatchResult matchResult = templateMatchingService.matchAndClassify(matForMatch);

            DocumentSide detectedSide = matchResult.detectedSide;
            int templateScore = matchResult.templateScore;
            int regionScore = matchResult.regionScore;

            if (detectedSide == DocumentSide.UNKNOWN) {
                detectedSide = expectedSide;
            }

            // [8] Score Calculation
            ScoreServiceImpl.ScoreInput scoreInput = new ScoreServiceImpl.ScoreInput();
            scoreInput.qualityScore      = qualityScore;
            scoreInput.cardDetected      = cardScore;
            scoreInput.aspectRatioScore  = aspectRatioScore;
            scoreInput.completenessScore = detection.cardCropped ? 0 : 100;
            scoreInput.templateScore     = templateScore;
            scoreInput.regionScore       = regionScore;

            int overallScore = scoreService.compute(scoreInput);

            // [9] Determine validity
            ValidationStatus status = determineSideStatus(overallScore);
            boolean valid = errors.isEmpty() && status == ValidationStatus.VALID;

            return CccdSideResult.builder()
                    .valid(valid)
                    .side(detectedSide)
                    .status(status)
                    .qualityScore(qualityScore)
                    .blurry(quality.isBlurry())
                    .cardDetected(detection.cardDetected)
                    .templateScore(templateScore)
                    .overallScore(overallScore)
                    .errors(errors)
                    .build();

        } finally {
            // Xóa buffer sau khi xử lý
            image.release();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CccdSideResult failResult(DocumentSide side, int qualityScore, int templateScore,
                                       List<ValidationErrorCode> errors) {
        return CccdSideResult.builder()
                .valid(false)
                .side(side)
                .status(ValidationStatus.INVALID)
                .qualityScore(qualityScore)
                .templateScore(templateScore)
                .overallScore(0)
                .blurry(false)
                .cardDetected(false)
                .errors(errors)
                .build();
    }

    private ValidationStatus determineSideStatus(int score) {
        int accept = props.getScore().getAccept();
        int review = props.getScore().getReview();
        if (score >= accept) return ValidationStatus.VALID;
        if (score >= review) return ValidationStatus.REVIEW;
        return ValidationStatus.INVALID;
    }

    private ValidationStatus determineStatus(int overallScore, boolean bothValid,
                                              CccdSideResult front, CccdSideResult back) {
        if (!bothValid) return ValidationStatus.INVALID;
        if (front.getSide() != DocumentSide.FRONT || back.getSide() != DocumentSide.BACK) {
            return ValidationStatus.INVALID;
        }
        return determineSideStatus(overallScore);
    }
}
