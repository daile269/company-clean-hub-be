-- ============================================================================
-- SCRIPT 2: TẠO KỊCH BẢN TEST - NHÂN VIÊN MỚI
-- ============================================================================
-- Mục đích: Tạo phân công cho nhân viên mới để test flow xác minh
-- Kết quả: 
--   - Tạo assignment mới
--   - Tự động tạo verification requirement (NEW_EMPLOYEE)
--   - Chỉ sinh 1 attendance cho ngày đầu tiên
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 1: Thiết lập thông tin test
SET @employee_code = 'NV001';  -- Mã nhân viên (đã reset ở script 1)
SET @contract_id = 1;           -- ID hợp đồng muốn phân công
SET @test_month = MONTH(CURDATE());
SET @test_year = YEAR(CURDATE());

-- Lấy employee_id
SET @employee_id = (SELECT id FROM employees WHERE employee_code = @employee_code);

-- Kiểm tra
SELECT 
    CASE 
        WHEN @employee_id IS NULL THEN 'ERROR: Không tìm thấy nhân viên!'
        WHEN @contract_id IS NULL THEN 'ERROR: Không tìm thấy hợp đồng!'
        ELSE 'OK: Sẵn sàng tạo test scenario'
    END AS status,
    @employee_id AS employee_id,
    @contract_id AS contract_id;

-- BƯỚC 2: Tạo assignment mới
INSERT INTO assignments (
    contract_id,
    employee_id,
    start_date,
    end_date,
    assignment_type,
    status,
    work_days,
    created_at,
    updated_at
)
VALUES (
    @contract_id,
    @employee_id,
    DATE_FORMAT(CURDATE(), '%Y-%m-01'),  -- Ngày đầu tháng
    LAST_DAY(CURDATE()),                  -- Ngày cuối tháng
    'PERMANENT',
    'ACTIVE',
    0,
    NOW(),
    NOW()
);

SET @assignment_id = LAST_INSERT_ID();

SELECT CONCAT('Đã tạo assignment ID: ', @assignment_id) AS result;

-- BƯỚC 3: Tạo verification requirement (NEW_EMPLOYEE)
INSERT INTO assignment_verifications (
    assignment_id,
    reason,
    status,
    max_attempts,
    current_attempts,
    created_at,
    updated_at
)
VALUES (
    @assignment_id,
    'NEW_EMPLOYEE',
    'PENDING',
    5,
    0,
    NOW(),
    NOW()
);

SET @verification_id = LAST_INSERT_ID();

SELECT CONCAT('Đã tạo verification ID: ', @verification_id) AS result;

-- BƯỚC 4: Tạo 1 attendance cho ngày đầu tiên (ngày làm việc đầu tiên)
-- Tìm ngày làm việc đầu tiên (không phải Chủ nhật)
SET @first_work_date = DATE_FORMAT(CURDATE(), '%Y-%m-01');
WHILE DAYOFWEEK(@first_work_date) = 1 DO  -- 1 = Sunday
    SET @first_work_date = DATE_ADD(@first_work_date, INTERVAL 1 DAY);
END WHILE;

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
    assignment_verification_id,
    created_at,
    updated_at
)
VALUES (
    @assignment_id,
    @employee_id,
    @first_work_date,
    8.0,
    0,
    0,
    0,
    FALSE,
    0,
    FALSE,
    @verification_id,
    NOW(),
    NOW()
);

SET @attendance_id = LAST_INSERT_ID();

SELECT CONCAT('Đã tạo attendance ID: ', @attendance_id, ' cho ngày: ', @first_work_date) AS result;

-- BƯỚC 5: Kiểm tra kết quả
SELECT 
    '=== KẾT QUẢ TẠO TEST SCENARIO ===' AS title;

SELECT 
    a.id AS assignment_id,
    a.employee_id,
    e.employee_code,
    e.name AS employee_name,
    a.contract_id,
    c.description AS contract_description,
    a.start_date,
    a.end_date,
    a.status AS assignment_status
FROM assignments a
INNER JOIN employees e ON a.employee_id = e.id
INNER JOIN contracts c ON a.contract_id = c.id
WHERE a.id = @assignment_id;

SELECT 
    av.id AS verification_id,
    av.assignment_id,
    av.reason,
    av.status,
    av.current_attempts,
    av.max_attempts,
    CASE 
        WHEN av.current_attempts < av.max_attempts THEN 'Có thể chụp'
        ELSE 'Đã hết lượt'
    END AS can_capture
FROM assignment_verifications av
WHERE av.id = @verification_id;

SELECT 
    att.id AS attendance_id,
    att.date,
    att.work_hours,
    att.assignment_verification_id,
    'Chờ nhân viên chụp ảnh' AS next_step
FROM attendances att
WHERE att.id = @attendance_id;

-- BƯỚC 6: Hướng dẫn test tiếp theo
SELECT 
    '=== HƯỚNG DẪN TEST ===' AS title,
    CONCAT('1. Nhân viên login với mã: ', @employee_code) AS step_1,
    '2. Vào trang "Chụp ảnh chấm công"' AS step_2,
    CONCAT('3. Chụp ảnh cho ngày: ', @first_work_date) AS step_3,
    '4. Kiểm tra ảnh đã được lưu vào verification_images' AS step_4,
    '5. Quản lý vào /admin/verifications để duyệt' AS step_5,
    '6. Sau khi duyệt, kiểm tra attendance còn lại đã được sinh' AS step_6;

-- ============================================================================
-- KẾT QUẢ MONG ĐỢI:
-- - 1 assignment mới
-- - 1 verification với status = PENDING, reason = NEW_EMPLOYEE
-- - 1 attendance cho ngày đầu tiên
-- - Nhân viên có thể chụp ảnh (can_capture = TRUE)
-- ============================================================================
