package com.company.company_clean_hub_be.entity;

public enum AssignmentStatus {
    SCHEDULED,      // Đã lên lịch (startDate trong tương lai, chưa bắt đầu)
    IN_PROGRESS,    // Đang làm việc
    COMPLETED,      // Hoàn thành
    TERMINATED,     // Bị ngưng giữa chừng
    CANCELLED        // Bị hủy
}
