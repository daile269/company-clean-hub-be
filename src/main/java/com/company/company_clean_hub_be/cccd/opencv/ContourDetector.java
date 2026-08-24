package com.company.company_clean_hub_be.cccd.opencv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Phát hiện contour hình chữ nhật (thẻ CCCD) trong ảnh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContourDetector {

    private final OpenCvImageProcessor imageProcessor;

    /**
     * Tìm contour lớn nhất có dạng tứ giác (4 điểm) trong ảnh.
     * @return List<Point2f> 4 góc, hoặc empty nếu không tìm thấy
     */
    public List<Point2f> detectCardContour(Mat image, double minAreaRatio) {
        int totalArea = image.rows() * image.cols();
        double minArea = totalArea * minAreaRatio;

        // 1. Grayscale
        Mat gray = imageProcessor.toGrayscale(image);

        // 2. GaussianBlur để giảm nhiễu
        Mat blurred = new Mat();
        GaussianBlur(gray, blurred, new Size(5, 5), 0);

        // 3. Canny edge detection
        Mat edges = new Mat();
        Canny(blurred, edges, 50, 150);

        // 4. Dilate để nối các cạnh bị gãy
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(3, 3));
        Mat dilated = new Mat();
        dilate(edges, dilated, kernel);

        // 5. Tìm contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(dilated, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        // 6. Tìm contour tứ giác lớn nhất
        List<Point2f> bestContour = new ArrayList<>();
        double bestArea = 0;

        for (long i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            if (area < minArea) continue;

            // ApproxPolyDP
            Mat approx = new Mat();
            double peri = arcLength(contour, true);
            approxPolyDP(contour, approx, 0.02 * peri, true);

            if (approx.rows() == 4) {
                if (area > bestArea && isConvex(approx)) {
                    bestArea = area;
                    bestContour = extractPoints(approx);
                }
            }
        }

        return bestContour;
    }

    /**
     * Kiểm tra contour có bị cắt mất góc không (quá gần mép ảnh).
     */
    public boolean isCardCropped(List<Point2f> corners, int imageWidth, int imageHeight, int margin) {
        for (Point2f pt : corners) {
            if (pt.x() < margin || pt.y() < margin
                    || pt.x() > imageWidth - margin
                    || pt.y() > imageHeight - margin) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tính aspect ratio từ 4 góc của thẻ (sau khi detect contour).
     */
    public double computeAspectRatioFromCorners(List<Point2f> corners) {
        if (corners.size() != 4) return -1;
        // Tính chiều rộng và chiều cao theo điểm góc
        double widthTop = distance(corners.get(0), corners.get(1));
        double widthBot = distance(corners.get(3), corners.get(2));
        double heightLeft = distance(corners.get(0), corners.get(3));
        double heightRight = distance(corners.get(1), corners.get(2));

        double avgWidth = (widthTop + widthBot) / 2.0;
        double avgHeight = (heightLeft + heightRight) / 2.0;

        return avgHeight > 0 ? avgWidth / avgHeight : -1;
    }

    private boolean isConvex(Mat approx) {
        return isContourConvex(approx);
    }

    private List<Point2f> extractPoints(Mat approx) {
        List<Point2f> pts = new ArrayList<>();
        for (int i = 0; i < approx.rows(); i++) {
            double x = approx.ptr(i).getFloat(0);
            double y = approx.ptr(i).getFloat(4);
            pts.add(new Point2f((float) x, (float) y));
        }
        return pts;
    }

    private double distance(Point2f a, Point2f b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Tạo 4 góc khung hình (dành cho ảnh đã được crop sẵn sát thẻ CCCD).
     */
    public List<Point2f> getFullFrameCorners(int width, int height) {
        List<Point2f> corners = new ArrayList<>();
        corners.add(new Point2f(0, 0));
        corners.add(new Point2f(width - 1, 0));
        corners.add(new Point2f(width - 1, height - 1));
        corners.add(new Point2f(0, height - 1));
        return corners;
    }
}
