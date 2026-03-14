-- ============================================================================
-- SCRIPT 5: TEST TỰ ĐỘNG DUYỆT SAU 5 LẦN
-- ============================================================================
-- Mục đích: Tạo verification đã chụp đủ 5 lần để test auto-approval
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 1: Thiết lập
SET @verification_id = 1;  -- ID verification muốn test

-- BƯỚC 2: Tạo 5 ảnh xác minh (mỗi ngày 1 ảnh)
INSERT INTO verification_images (
    assignment_verification_id,
    employee_id,
    attendance_id,
    cloudinary_public_id,
    cloudinary_url,
    latitude,
    longitude,
    address,
    captured_at,
    face_confidence,
    image_quality_score,
    created_at
)
SELECT 
    av.id,
    a.employee_id,
    NULL,
    CONCAT('test_auto_', av.id, '_day1'),
    CONCAT('https://res.cloudinary.com/test/verification_day1.jpg'),
    21.028511,
    105.804817,
    'Test Address Day 1',
    DATE_SUB(NOW(), INTERVAL 4 DAY),
    0.95,
    0.90,
    DATE_SUB(NOW(), INTERVAL 4 DAY)
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

INSERT INTO verification_images (
    assignment_verification_id, employee_id, attendance_id,
    cloudinary_public_id, cloudinary_url,
    latitude, longitude, address,
    captured_at, face_confidence, image_quality_score, created_at
)
SELECT 
    av.id, a.employee_id, NULL,
    CONCAT('test_auto_', av.id, '_day2'),
    'https://res.cloudinary.com/test/verification_day2.jpg',
    21.028511, 105.804817, 'Test Address Day 2',
    DATE_SUB(NOW(), INTERVAL 3 DAY), 0.93, 0.88,
    DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

INSERT INTO verification_images (
    assignment_verification_id, employee_id, attendance_id,
    cloudinary_public_id, cloudinary_url,
    latitude, longitude, address,
    captured_at, face_confidence, image_quality_score, created_at
)
SELECT 
    av.id, a.employee_id, NULL,
    CONCAT('test_auto_', av.id, '_day3'),
    'https://res.cloudinary.com/test/verification_day3.jpg',
    21.028511, 105.804817, 'Test Address Day 3',
    DATE_SUB(NOW(), INTERVAL 2 DAY), 0.91, 0.87,
    DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

INSERT INTO verification_images (
    assignment_verification_id, employee_id, attendance_id,
    cloudinary_public_id, cloudinary_url,
    latitude, longitude, address,
    captured_at, face_confidence, image_quality_score, created_at
)
SELECT 
    av.id, a.employee_id, NULL,
    CONCAT('test_auto_', av.id, '_day4'),
    'https://res.cloudinary.com/test/verification_day4.jpg',
    21.028511, 105.804817, 'Test Address Day 4',
    DATE_SUB(NOW(), INTERVAL 1 DAY), 0.94, 0.89,
    DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

INSERT INTO verification_images (
    assignment_verification_id, employee_id, attendance_id,
    cloudinary_public_id, cloudinary_url,
    latitude, longitude, address,
    captured_at, face_confidence, image_quality_score, created_at
)
SELECT 
    av.id, a.employee_id, NULL,
    CONCAT('test_auto_', av.id, '_day5'),
    'https://res.cloudinary.com/test/verification_day5.jpg',
    21.028511, 105.804817, 'Test Address Day 5',
    NOW(), 0.96, 0.91, NOW()
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

-- BƯỚC 3: Cập nhật verification status
UPDATE assignment_verifications
SET status = 'IN_PROGRESS',
    current_attempts = 5,
    updated_at = NOW()
WHERE id = @verification_id;

SELECT CONCAT('Đã tạo 5 ảnh xác minh cho verification ID: ', @verification_id) AS result;

-- BƯỚC 4: Kiểm tra verification đủ điều kiện auto-approve
SELECT 
    av.id AS verification_id,
    av.assignment_id,
    e.employee_code,
    e.name AS employee_name,
    av.status,
    av.current_attempts,
    av.max_attempts,
    CASE 
        WHEN av.status = 'IN_PROGRESS' AND av.current_attempts >= av.max_attempts 
        THEN 'ĐỦ ĐIỀU KIỆN TỰ ĐỘNG DUYỆT'
        ELSE 'Chưa đủ điều kiện'
    END AS auto_approval_status,
    COUNT(vi.id) AS total_images
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
WHERE av.id = @verification_id
GROUP BY av.id, av.assignment_id, e.employee_code, e.name, 
         av.status, av.current_attempts, av.max_attempts;

-- BƯỚC 5: Xem danh sách ảnh đã chụp
SELECT 
    vi.id,
    DATE(vi.captured_at) AS capture_date,
    vi.latitude,
    vi.longitude,
    vi.face_confidence,
    vi.image_quality_score
FROM verification_images vi
WHERE vi.assignment_verification_id = @verification_id
ORDER BY vi.captured_at;

-- ============================================================================
-- HƯỚNG DẪN TEST AUTO-APPROVAL:
-- 
-- CÁCH 1: Chạy scheduled job thủ công (trong code)
-- - Gọi API hoặc trigger method: verificationService.processAutoApprovals()
-- 
-- CÁCH 2: Đợi scheduled job tự chạy
-- - Job chạy lúc 1:00 AM, 7:00 AM, 1:00 PM, 7:00 PM mỗi ngày
-- 
-- CÁCH 3: Test thủ công bằng SQL
-- - Chạy script 06_manual_auto_approval.sql
-- ============================================================================
