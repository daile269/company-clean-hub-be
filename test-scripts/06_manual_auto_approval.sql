-- ============================================================================
-- SCRIPT 6: THỰC HIỆN AUTO-APPROVAL THỦ CÔNG
-- ============================================================================
-- Mục đích: Mô phỏng scheduled job auto-approval để test ngay
-- Lưu ý: Trong production, job này chạy tự động
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 1: Tìm các verification đủ điều kiện auto-approve
SELECT 
    av.id AS verification_id,
    av.assignment_id,
    e.employee_code,
    e.name AS employee_name,
    av.reason,
    av.status,
    av.current_attempts,
    av.max_attempts,
    COUNT(vi.id) AS total_images,
    'ĐỦ ĐIỀU KIỆN TỰ ĐỘNG DUYỆT' AS note
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
WHERE av.status = 'IN_PROGRESS'
  AND av.current_attempts >= av.max_attempts
GROUP BY av.id, av.assignment_id, e.employee_code, e.name, 
         av.reason, av.status, av.current_attempts, av.max_attempts;

-- BƯỚC 2: Thực hiện auto-approve
UPDATE assignment_verifications
SET status = 'AUTO_APPROVED',
    auto_approved_at = NOW(),
    updated_at = NOW()
WHERE status = 'IN_PROGRESS'
  AND current_attempts >= max_attempts;

SELECT CONCAT('Đã tự động duyệt ', ROW_COUNT(), ' verification') AS result;

-- BƯỚC 3: Sinh attendance còn lại cho các verification vừa được auto-approve
-- Lưu ý: Trong code, logic này được xử lý bởi triggerAttendanceGeneration()
-- Script này chỉ mô phỏng, trong thực tế cần chạy qua backend

SELECT 
    '=== CÁC VERIFICATION ĐÃ ĐƯỢC TỰ ĐỘNG DUYỆT ===' AS title;

SELECT 
    av.id AS verification_id,
    av.assignment_id,
    e.employee_code,
    e.name AS employee_name,
    av.status,
    av.auto_approved_at,
    a.start_date,
    a.end_date,
    'Cần sinh attendance từ ngày sau ngày chụp ảnh cuối' AS next_action
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
WHERE av.status = 'AUTO_APPROVED'
  AND av.auto_approved_at IS NOT NULL
ORDER BY av.auto_approved_at DESC;

-- BƯỚC 4: Kiểm tra ngày chụp ảnh cuối cùng để biết sinh attendance từ ngày nào
SELECT 
    av.id AS verification_id,
    av.assignment_id,
    MAX(DATE(vi.captured_at)) AS last_capture_date,
    DATE_ADD(MAX(DATE(vi.captured_at)), INTERVAL 1 DAY) AS generate_from_date,
    'Sinh attendance từ ngày này' AS note
FROM assignment_verifications av
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
WHERE av.status = 'AUTO_APPROVED'
  AND av.auto_approved_at IS NOT NULL
GROUP BY av.id, av.assignment_id;

-- ============================================================================
-- LƯU Ý QUAN TRỌNG:
-- 
-- Script này CHỈ cập nhật status thành AUTO_APPROVED.
-- Để sinh attendance còn lại, cần:
-- 
-- CÁCH 1: Gọi API backend
-- POST /api/verifications/approve
-- Body: { "verificationId": <id> }
-- 
-- CÁCH 2: Restart backend để trigger logic sinh attendance
-- Backend sẽ tự động phát hiện verification đã AUTO_APPROVED
-- và sinh attendance còn lại
-- 
-- CÁCH 3: Chạy script 07_generate_remaining_attendances.sql
-- ============================================================================
