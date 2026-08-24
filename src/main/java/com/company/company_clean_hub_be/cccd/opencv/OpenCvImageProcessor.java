package com.company.company_clean_hub_be.cccd.opencv;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Tiện ích xử lý ảnh OpenCV cơ bản.
 * Chứa các phép tính chất lượng: blur, brightness, contrast.
 */
@Slf4j
@Component
public class OpenCvImageProcessor {

    /**
     * Decode byte[] thành OpenCV Mat.
     * @return null nếu không decode được
     */
    public Mat decodeImage(byte[] imageBytes) {
        try {
            Mat buf = new Mat(1, imageBytes.length, CV_8UC1);
            buf.data().put(imageBytes);
            Mat mat = opencv_imgcodecs.imdecode(buf, opencv_imgcodecs.IMREAD_COLOR);
            if (mat == null || mat.empty()) return null;
            return mat;
        } catch (Exception e) {
            log.warn("[CCCD] Image decode failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tính variance of Laplacian để phát hiện ảnh mờ.
     * Giá trị càng thấp → ảnh càng mờ.
     */
    public double computeBlurVariance(Mat image) {
        Mat gray = toGrayscale(image);
        Mat laplacian = new Mat();
        opencv_imgproc.Laplacian(gray, laplacian, opencv_core.CV_64F);
        Mat mean = new Mat();
        Mat stddev = new Mat();
        meanStdDev(laplacian, mean, stddev);
        double std = stddev.createIndexer().getDouble(0);
        return std * std;
    }

    /**
     * Tính giá trị brightness trung bình (0–255).
     */
    public double computeBrightness(Mat image) {
        Mat gray = toGrayscale(image);
        Mat mean = new Mat();
        Mat stddev = new Mat();
        meanStdDev(gray, mean, stddev);
        return mean.createIndexer().getDouble(0);
    }

    /**
     * Tính độ contrast (standard deviation of grayscale).
     */
    public double computeContrast(Mat image) {
        Mat gray = toGrayscale(image);
        Mat mean = new Mat();
        Mat stddev = new Mat();
        meanStdDev(gray, mean, stddev);
        return stddev.createIndexer().getDouble(0);
    }

    /**
     * Resize ảnh về kích thước chuẩn để so sánh template.
     */
    public Mat resize(Mat image, int width, int height) {
        Mat resized = new Mat();
        opencv_imgproc.resize(image, resized, new Size(width, height));
        return resized;
    }

    /**
     * Chuyển ảnh sang grayscale.
     */
    public Mat toGrayscale(Mat image) {
        if (image.channels() == 1) return image;
        Mat gray = new Mat();
        cvtColor(image, gray, COLOR_BGR2GRAY);
        return gray;
    }

    /**
     * Kiểm tra MIME type thực sự từ byte header (magic bytes).
     */
    public boolean isValidImageMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return true;
        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return true;
        // WebP: 52 49 46 46 ... 57 45 42 50
        if ((bytes[0] & 0xFF) == 0x52 && (bytes[1] & 0xFF) == 0x49 && bytes.length >= 12
                && (bytes[8] & 0xFF) == 0x57 && (bytes[9] & 0xFF) == 0x45) return true;
        return false;
    }
}
