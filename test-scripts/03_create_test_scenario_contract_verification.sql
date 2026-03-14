-- ============================================================================
-- SCRIPT 3: TẠO KỊCH BẢN TEST - HỢP ĐỒNG YÊU CẦU XÁC MINH
-- ============================================================================
-- Mục đích: Test flow xác minh với hợp đồng có requires_image_verification = TRUE
-- Kết quả: 
--   - Bật cờ requires_image_verification cho hợp đồng
--   - Tạo assignment cho nhân viên cũ (đã có lịch sử)
--   - Vẫn phải xác minh vì hợp đồng yêu cầu
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 1: Thiết lập thông tin test
SET @employee_code = 'NV002';  -- Nhân viên CŨ (đã có lịch sử làm việc)
SET @contract_id = 2;           -- Hợp đồng muốn test

-- Lấy employee_id
SET @employee_id = (SELECT id FROM employees WHERE employee_code = @employee_code);

-- Kiểm tra nhân viên có lịch sử không
SELECT 
    e.id AS employee_id,
    e.employee_code,
    e.name,
    COUNT(DISTINCT av.id) AS completed_verifications,
    CASE 
        WHEN COUNT(DISTINCT av.id) > 0 THEN 'Nhân viên CŨ (có lịch sử)'
        ELSE 'Nhân viên MỚI (chưa có lịch sử)'
    END AS employee_status
FROM employees e
LEFT JOIN assignments a ON e.id = a.employee_id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id 
    AND av.status IN ('APPROVED', 'AUTO_APPROVED')
WHERE e.id = @employee_id
GROUP BY e.id, e.employee_code, e.name;

-- BƯỚC 2: Bật cờ requires_image_verification cho hợp đồng
UPDATE contracts 
SET requires_image_verification = TRUE,
    updated_at = NOW()
WHERE id = @contract_id;

SELECT 
    c.id AS contract_id,
    c.description,
    c.requires_image_verification,
    CASE 
        WHEN c.requires_image_verification = TRUE THEN 'Hợp đồng YÊU CẦU xác minh'
        ELSE 'Hợp đồng KHÔNG yêu cầu xác minh'
    END AS verification_requirement
FROM contracts c
WHERE c.id = @contract_id;

-- BƯỚC 3: Tạo assignment mới
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
    DATE_FORMAT(CURDATE(), '%Y-%m-01'),
    LAST_DAY(CURDATE()),
    'PERMANENT',
    'ACTIVE',
    0,
    NOW(),
    NOW()
);

SET @assignment_id = LAST_INSERT_ID();

SELECT CONCAT('Đã tạo assignment ID: ', @assignment_id) AS result;

-- BƯỚC 4: Tạo verification requirement (CONTRACT_SETTING)
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
    'CONTRACT_SETTING',
    'PENDING',
    5,
    0,
    NOW(),
    NOW()
);

SET @verification_id = LAST_INSERT_ID();

SELECT CONCAT('Đã tạo verification ID: ', @verification_id, ' với lý do: CONTRACT_SETTING') AS result;

-- BƯỚC 5: Tạo 1 attendance cho ngày đầu tiên
SET @first_work_date = DATE_FORMAT(CURDATE(), '%Y-%m-01');
WHILE DAYOFWEEK(@first_work_date) = 1 DO
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

-- BƯỚC 6: Kiểm tra kết quả
SELECT '=== KẾT QUẢ TEST SCENARIO: HỢP ĐỒNG YÊU CẦU XÁC MINH ===' AS title;

SELECT 
    av.id AS verification_id,
    av.reason,
    av.status,
    e.employee_code,
    e.name AS employee_name,
    c.description AS contract_description,
    c.requires_image_verification,
    'Nhân viên CŨ nhưng vẫn phải xác minh vì hợp đồng yêu cầu' AS note
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
INNER JOIN contracts c ON a.contract_id = c.id
WHERE av.id = @verification_id;

-- ============================================================================
-- KẾT QUẢ MONG ĐỢI:
-- - Hợp đồng có requires_image_verification = TRUE
-- - Nhân viên CŨ (có lịch sử) vẫn phải xác minh
-- - Verification reason = CONTRACT_SETTING
-- - Chỉ có 1 attendance cho ngày đầu
-- ============================================================================
