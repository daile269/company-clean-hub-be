package com.company.company_clean_hub_be.cccd.opencv;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.opencv.opencv_core.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * Thực hiện Perspective Correction: chỉnh thẳng ảnh CCCD bị chụp nghiêng.
 */
@Slf4j
@Component
public class PerspectiveTransformer {

    private static final int STANDARD_WIDTH = 856;
    private static final int STANDARD_HEIGHT = 540;

    /**
     * Sắp xếp 4 góc theo thứ tự: TL, TR, BR, BL
     * và áp dụng warpPerspective về kích thước chuẩn CCCD.
     */
    public Mat correctPerspective(Mat image, List<Point2f> corners) {
        if (corners.size() != 4) return image;

        List<Point2f> ordered = orderCorners(corners);

        // Tạo ma trận destination (CCCD chuẩn)
        Mat srcMat = buildPointsMat(ordered);
        Mat dstMat = buildDestMat(STANDARD_WIDTH, STANDARD_HEIGHT);

        Mat transform = getPerspectiveTransform(srcMat, dstMat);
        Mat result = new Mat();
        warpPerspective(image, result, transform, new Size(STANDARD_WIDTH, STANDARD_HEIGHT));

        return result;
    }

    /**
     * Sắp xếp 4 điểm theo thứ tự: TL, TR, BR, BL
     */
    public List<Point2f> orderCorners(List<Point2f> pts) {
        // TL: tổng nhỏ nhất (x+y)
        // BR: tổng lớn nhất
        // TR: hiệu nhỏ nhất (y-x)
        // BL: hiệu lớn nhất
        Point2f tl = pts.get(0), tr = pts.get(0), br = pts.get(0), bl = pts.get(0);
        double minSum = Double.MAX_VALUE, maxSum = -Double.MAX_VALUE;
        double minDiff = Double.MAX_VALUE, maxDiff = -Double.MAX_VALUE;

        for (Point2f pt : pts) {
            double sum = pt.x() + pt.y();
            double diff = pt.y() - pt.x();
            if (sum < minSum) { minSum = sum; tl = pt; }
            if (sum > maxSum) { maxSum = sum; br = pt; }
            if (diff < minDiff) { minDiff = diff; tr = pt; }
            if (diff > maxDiff) { maxDiff = diff; bl = pt; }
        }

        List<Point2f> ordered = new ArrayList<>();
        ordered.add(tl); ordered.add(tr);
        ordered.add(br); ordered.add(bl);
        return ordered;
    }

    private Mat buildPointsMat(List<Point2f> pts) {
        Mat mat = new Mat(4, 1, org.bytedeco.opencv.global.opencv_core.CV_32FC2);
        for (int i = 0; i < 4; i++) {
            mat.ptr(i).putFloat(pts.get(i).x());
            mat.ptr(i).putFloat(4, pts.get(i).y());
        }
        return mat;
    }

    private Mat buildDestMat(int w, int h) {
        Mat mat = new Mat(4, 1, org.bytedeco.opencv.global.opencv_core.CV_32FC2);
        float[][] pts = {{0, 0}, {w, 0}, {w, h}, {0, h}};
        for (int i = 0; i < 4; i++) {
            mat.ptr(i).putFloat(pts[i][0]);
            mat.ptr(i).putFloat(4, pts[i][1]);
        }
        return mat;
    }
}
