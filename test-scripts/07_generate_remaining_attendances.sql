-- ============================================================================
-- SCRIPT 7: SINH ATTENDANCE CÒN LẠI SAU KHI DUYỆT
-- ============================================================================
-- Mục đích: Mô phỏng logic sinh attendance sau khi verification được duyệt
-- Lưu ý: Trong production, logic này chạy tự động trong backend
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 1: Thiết lập
SET @assignment_id = 1;  -- ID assignment cần sinh attendance

-- Lấy thông tin assignment
SELECT 
    a.id AS assignment_id,
    a.employee_id,
    e.employee_code,
    e.name AS employee_name,
    a.start_date,
    a.end_date,
    av.status AS verification_status,
    MAX(DATE(vi.captured_at)) AS last_capture_date
FROM assignments a
INNER JOIN employees e ON a.employee_id = e.id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
WHERE a.id = @assignment_id
GROUP BY a.id, a.employee_id, e.employee_code, e.name, 
         a.start_date, a.end_date, av.status;

-- BƯỚC 2: Tính toán ngày bắt đầu sinh attendance
-- Sinh từ ngày sau ngày chụp ảnh cuối cùng
SET @last_capture_date = (
    SELECT MAX(DATE(vi.captured_at))
    FROM verification_images vi
    INNER JOIN assignment_verifications av ON vi.assignment_verification_id = av.id
    WHERE av.assignment_id = @assignment_id
);

SET @start_generate_date = DATE_ADD(@last_capture_date, INTERVAL 1 DAY);
SET @end_date = LAST_DAY(@last_capture_date);  -- Cuối tháng

SELECT 
    @last_capture_date AS last_capture_date,
    @start_generate_date AS start_generate_date,
    @end_date AS end_date,
    DATEDIFF(@end_date, @start_generate_date) + 1 AS total_days,
    'Sẽ sinh attendance cho các ngày này (trừ Chủ nhật)' AS note;

-- BƯỚC 3: Sinh attendance cho các ngày còn lại (trừ Chủ nhật)
-- Tạo bảng tạm chứa các ngày cần sinh
DROP TEMPORARY TABLE IF EXISTS temp_dates;
CREATE TEMPORARY TABLE temp_dates (
    work_date DATE
);

-- Sinh các ngày từ start đến end
SET @current_date = @start_generate_date;
WHILE @current_date <= @end_date DO
    -- Chỉ thêm nếu không phải Chủ nhật (DAYOFWEEK = 1)
    IF DAYOFWEEK(@current_date) != 1 THEN
        INSERT INTO temp_dates (work_date) VALUES (@current_date);
    END IF;
    SET @current_date = DATE_ADD(@current_date, INTERVAL 1 DAY);
END WHILE;

-- Xem các ngày sẽ sinh
SELECT 
    work_date,
    DAYNAME(work_date) AS day_name,
    'Sẽ sinh attendance' AS action
FROM temp_dates
ORDER BY work_date;

-- BƯỚC 4: Sinh attendance (chỉ những ngày chưa có)
INSERT INTO attendances (
    assignment_id,
    employee_id,
    date,
    work_hours,
    bonus,
    penalty,
    support_cost,
    is_overtime,
    overtime_amount,
    deleted,
    created_at,
    updated_at
)
SELECT 
    a.id,
    a.employee_id,
    td.work_date,
    8.0,
    0,
    0,
    0,
    FALSE,
    0,
    FALSE,
    NOW(),
    NOW()
FROM assignments a
CROSS JOIN temp_dates td
WHERE a.id = @assignment_id
  AND NOT EXISTS (
      SELECT 1 FROM attendances att
      WHERE att.assignment_id = a.id
        AND att.employee_id = a.employee_id
        AND att.date = td.work_date
  );

SELECT CONCAT('Đã sinh ', ROW_COUNT(), ' attendance mới') AS result;

-- BƯỚC 5: Cập nhật work_days của assignment
UPDATE assignments a
SET work_days = (
    SELECT COUNT(*)
    FROM attendances att
    WHERE att.assignment_id = a.id
      AND att.deleted = FALSE
),
updated_at = NOW()
WHERE a.id = @assignment_id;

-- BƯỚC 6: Kiểm tra kết quả
SELECT 
    '=== KẾT QUẢ SINH ATTENDANCE ===' AS title;

SELECT 
    a.id AS assignment_id,
    e.employee_code,
    e.name AS employee_name,
    a.work_days AS total_work_days,
    COUNT(att.id) AS attendance_count,
    MIN(att.date) AS first_attendance,
    MAX(att.date) AS last_attendance
FROM assignments a
INNER JOIN employees e ON a.employee_id = e.id
LEFT JOIN attendances att ON a.id = att.assignment_id AND att.deleted = FALSE
WHERE a.id = @assignment_id
GROUP BY a.id, e.employee_code, e.name, a.work_days;

-- Xem chi tiết attendance theo ngày
SELECT 
    att.id,
    att.date,
    DAYNAME(att.date) AS day_name,
    att.work_hours,
    att.deleted
FROM attendances att
WHERE att.assignment_id = @assignment_id
ORDER BY att.date;

-- Dọn dẹp
DROP TEMPORARY TABLE IF EXISTS temp_dates;

-- ============================================================================
-- KẾT QUẢ MONG ĐỢI:
-- - Attendance được sinh từ ngày sau ngày chụp ảnh cuối
-- - Không sinh cho Chủ nhật
-- - Không sinh trùng với attendance đã có
-- - work_days được cập nhật chính xác
-- ============================================================================
