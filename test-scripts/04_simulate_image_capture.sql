-- ============================================================================
-- SCRIPT 4: MÔ PHỎNG CHỤP ẢNH XÁC MINH
-- ============================================================================
-- Mục đích: Tạo dữ liệu ảnh xác minh giả để test flow duyệt
-- Lưu ý: Trong thực tế, ảnh được upload qua API, script này chỉ để test
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 1: Thiết lập thông tin
SET @verification_id = 1;  -- ID verification muốn test
SET @attendance_id = 1;     -- ID attendance tương ứng

-- Lấy thông tin verification
SELECT 
    av.id AS verification_id,
    av.assignment_id,
    a.employee_id,
    e.employee_code,
    e.name AS employee_name,
    av.status,
    av.current_attempts,
    av.max_attempts,
    att.date AS attendance_date
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
LEFT JOIN attendances att ON att.id = @attendance_id
WHERE av.id = @verification_id;

-- BƯỚC 2: Tạo ảnh xác minh giả (lần 1)
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
    @attendance_id,
    CONCAT('test_verification_', av.id, '_', UNIX_TIMESTAMP()),
    CONCAT('https://res.cloudinary.com/test/image/upload/verification_', av.id, '_1.jpg'),
    21.028511,  -- Latitude Hà Nội
    105.804817, -- Longitude Hà Nội
    'Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội',
    NOW(),
    0.95,
    0.88,
    NOW()
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

-- BƯỚC 3: Cập nhật verification status và attempts
UPDATE assignment_verifications
SET status = 'IN_PROGRESS',
    current_attempts = current_attempts + 1,
    updated_at = NOW()
WHERE id = @verification_id;

SELECT CONCAT('Đã tạo ảnh xác minh lần 1 cho verification ID: ', @verification_id) AS result;

-- BƯỚC 4: Kiểm tra kết quả
SELECT 
    vi.id AS image_id,
    vi.assignment_verification_id,
    vi.employee_id,
    vi.attendance_id,
    vi.cloudinary_url,
    vi.latitude,
    vi.longitude,
    vi.address,
    vi.captured_at,
    vi.face_confidence,
    vi.image_quality_score
FROM verification_images vi
WHERE vi.assignment_verification_id = @verification_id
ORDER BY vi.captured_at DESC;

SELECT 
    av.id AS verification_id,
    av.status,
    av.current_attempts,
    av.max_attempts,
    CASE 
        WHEN av.current_attempts < av.max_attempts THEN 'Có thể chụp thêm'
        ELSE 'Đã hết lượt chụp'
    END AS can_capture_more
FROM assignment_verifications av
WHERE av.id = @verification_id;

-- ============================================================================
-- HƯỚNG DẪN TEST TIẾP THEO:
-- 1. Vào /admin/verifications để xem yêu cầu
-- 2. Click "Xem chi tiết" để xem ảnh
-- 3. Click "Duyệt xác minh" để approve
-- 4. Kiểm tra attendance còn lại đã được sinh chưa
-- ============================================================================

-- BONUS: Script để tạo nhiều ảnh (test 5 lần)
-- Uncomment để chạy
/*
-- Tạo ảnh lần 2
INSERT INTO verification_images (
    assignment_verification_id, employee_id, attendance_id,
    cloudinary_public_id, cloudinary_url,
    latitude, longitude, address,
    captured_at, face_confidence, image_quality_score, created_at
)
SELECT 
    av.id, a.employee_id, @attendance_id,
    CONCAT('test_verification_', av.id, '_', UNIX_TIMESTAMP(), '_2'),
    CONCAT('https://res.cloudinary.com/test/image/upload/verification_', av.id, '_2.jpg'),
    21.028511, 105.804817, 'Số 1 Đại Cồ Việt, Hai Bà Trưng, Hà Nội',
    DATE_ADD(NOW(), INTERVAL 1 DAY), 0.92, 0.85, DATE_ADD(NOW(), INTERVAL 1 DAY)
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE av.id = @verification_id;

UPDATE assignment_verifications
SET current_attempts = current_attempts + 1, updated_at = NOW()
WHERE id = @verification_id;

-- Lặp lại cho lần 3, 4, 5...
*/
