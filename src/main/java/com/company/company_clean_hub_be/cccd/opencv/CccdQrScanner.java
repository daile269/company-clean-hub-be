package com.company.company_clean_hub_be.cccd.opencv;

import com.company.company_clean_hub_be.cccd.dto.CccdExtractedData;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Trích xuất dữ liệu từ QR Code trên thẻ CCCD bằng ZXing.
 * Cấu trúc QR Code CCCD Việt Nam:
 * SốCCCD|SốCMNDCũ|HọVàTên|NgàySinh|GiớiTính|ĐịaChỉ|NgàyCấp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CccdQrScanner {

    /**
     * Đọc QR Code từ OpenCV Mat.
     * @return CccdExtractedData nếu quét thành công, null nếu không đọc được QR
     */
    /**
     * Đọc QR Code từ OpenCV Mat.
     */
    public CccdExtractedData scanQrCode(Mat image) {
        if (image == null || image.empty()) return null;

        try {
            BufferedImage bufferedImage = matToBufferedImage(image);
            return scanQrFromBufferedImage(bufferedImage);
        } catch (Exception e) {
            log.warn("[CCCD] Error scanning QR code: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Đọc QR Code từ byte array nguyên bản.
     */
    public CccdExtractedData scanQrFromBytes(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return null;
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            return scanQrFromBufferedImage(bufferedImage);
        } catch (Exception e) {
            log.warn("[CCCD] Error scanning QR from bytes: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decode QR Code từ BufferedImage bằng ZXing đa chiến lược (Multi-pass & Multi-angle: 0°, 90°, 270°, 180°).
     */
    public CccdExtractedData scanQrFromBufferedImage(BufferedImage originalImg) {
        if (originalImg == null) return null;

        int[] angles = {0, 90, 270, 180};
        for (int angle : angles) {
            BufferedImage bufferedImage = rotateImage(originalImg, angle);
            if (bufferedImage == null) continue;

            // Pass 1: Decode toàn bộ ảnh
            Result result = tryDecodeSingleImage(bufferedImage);

            // Pass 2: Crop vùng góc trên bên phải 2x (Vị trí chứa QR Code chuẩn của thẻ CCCD)
            if (result == null) {
                BufferedImage topRight2x = cropAndScale(bufferedImage, 0.45, 0.0, 0.55, 0.55, 2.0);
                result = tryDecodeSingleImage(topRight2x);
            }

            // Pass 3: Crop vùng góc trên bên phải 3x
            if (result == null) {
                BufferedImage topRight3x = cropAndScale(bufferedImage, 0.45, 0.0, 0.55, 0.55, 3.0);
                result = tryDecodeSingleImage(topRight3x);
            }

            // Pass 4: Crop vùng góc trên bên trái 2x (Phòng trường hợp ảnh bị lộn)
            if (result == null) {
                BufferedImage topLeft2x = cropAndScale(bufferedImage, 0.0, 0.0, 0.55, 0.55, 2.0);
                result = tryDecodeSingleImage(topLeft2x);
            }

            if (result != null && result.getText() != null) {
                String qrText = result.getText().trim();
                log.info("[CCCD] QR Code decoded successfully at angle {}°: {}", angle, qrText);
                return parseCccdQrText(qrText);
            }
        }

        log.warn("[CCCD] QR Code scan failed across all angles (0°, 90°, 270°, 180°) and passes");
        return null;
    }

    private BufferedImage rotateImage(BufferedImage src, int angle) {
        if (angle == 0 || src == null) return src;
        try {
            int w = src.getWidth();
            int h = src.getHeight();
            boolean is90or270 = (angle == 90 || angle == 270);
            int newW = is90or270 ? h : w;
            int newH = is90or270 ? w : h;
            BufferedImage rotated = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = rotated.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.translate((newW - w) / 2.0, (newH - h) / 2.0);
            g.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);
            g.drawImage(src, 0, 0, null);
            g.dispose();
            return rotated;
        } catch (Exception e) {
            return src;
        }
    }

    private Result tryDecodeSingleImage(BufferedImage img) {
        if (img == null) return null;

        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE));

        // 1. Thuật toán HybridBinarizer
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result res = new MultiFormatReader().decode(bitmap, hints);
            if (res != null && res.getText() != null && !res.getText().isBlank()) {
                return res;
            }
        } catch (Exception ignored) {}

        // 2. Thuật toán GlobalHistogramBinarizer
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(img);
            BinaryBitmap bitmap = new BinaryBitmap(new com.google.zxing.common.GlobalHistogramBinarizer(source));
            Result res = new MultiFormatReader().decode(bitmap, hints);
            if (res != null && res.getText() != null && !res.getText().isBlank()) {
                return res;
            }
        } catch (Exception ignored) {}

        // 3. Thử chuyển sang Grayscale sắc nét
        try {
            BufferedImage grayImg = toGrayscale(img);
            LuminanceSource source = new BufferedImageLuminanceSource(grayImg);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result res = new MultiFormatReader().decode(bitmap, hints);
            if (res != null && res.getText() != null && !res.getText().isBlank()) {
                return res;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private BufferedImage toGrayscale(BufferedImage src) {
        if (src == null) return null;
        try {
            BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            java.awt.Graphics2D g = dest.createGraphics();
            g.drawImage(src, 0, 0, null);
            g.dispose();
            return dest;
        } catch (Exception e) {
            return src;
        }
    }

    private BufferedImage cropAndScale(BufferedImage src, double rx, double ry, double rw, double rh, double scale) {
        try {
            int x = (int) (src.getWidth() * rx);
            int y = (int) (src.getHeight() * ry);
            int w = Math.min((int) (src.getWidth() * rw), src.getWidth() - x);
            int h = Math.min((int) (src.getHeight() * rh), src.getHeight() - y);
            if (w <= 0 || h <= 0) return null;

            BufferedImage cropped = src.getSubimage(x, y, w, h);
            int newW = (int) (w * scale);
            int newH = (int) (h * scale);
            BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cropped, 0, 0, newW, newH, null);
            g.dispose();
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse chuỗi QR CCCD Việt Nam.
     * Ví dụ: 079198000123||NGUYỄN VĂN A|15101998|Nam|123 Nguyễn Trãi, P.2, Q.5, TP.HCM|20102021
     */
    public CccdExtractedData parseCccdQrText(String text) {
        if (text == null || !text.contains("|")) return null;

        String[] parts = text.split("\\|", -1);
        if (parts.length < 3) return null;

        String idCard = parts[0].trim();
        String fullName = parts.length > 2 ? parts[2].trim() : "";
        String dobRaw = parts.length > 3 ? parts[3].trim() : "";
        String gender = parts.length > 4 ? parts[4].trim() : "";
        String address = parts.length > 5 ? parts[5].trim() : "";

        // Format DDMMYYYY -> DD/MM/YYYY
        String dobFormatted = formatDob(dobRaw);

        return CccdExtractedData.builder()
                .idCard(idCard.matches("^\\d{12}$") ? idCard : (idCard.matches("^\\d{9}$") ? idCard : null))
                .fullName(fullName.isEmpty() ? null : fullName.toUpperCase())
                .dateOfBirth(dobFormatted)
                .gender(gender.isEmpty() ? null : gender)
                .address(address.isEmpty() ? null : address)
                .build();
    }

    private String formatDob(String dobRaw) {
        if (dobRaw == null || dobRaw.length() != 8) return dobRaw;
        try {
            String day = dobRaw.substring(0, 2);
            String month = dobRaw.substring(2, 4);
            String year = dobRaw.substring(4, 8);
            return day + "/" + month + "/" + year;
        } catch (Exception e) {
            return dobRaw;
        }
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        try {
            BytePointer buf = new BytePointer();
            opencv_imgcodecs.imencode(".png", mat, buf);
            byte[] bytes = new byte[(int) buf.limit()];
            buf.get(bytes);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("[CCCD] Failed to convert Mat to BufferedImage: {}", e.getMessage());
            return null;
        }
    }
}
