-- ============================================================================
-- SCRIPT 8: KIỂM TRA TRẠNG THÁI XÁC MINH
-- ============================================================================
-- Mục đích: Xem tổng quan tất cả verification trong hệ thống
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- TỔNG QUAN VERIFICATION
SELECT 
    '=== TỔNG QUAN VERIFICATION ===' AS title;

SELECT 
    av.status,
    COUNT(*) AS total,
    COUNT(DISTINCT av.assignment_id) AS unique_assignments,
    COUNT(DISTINCT a.employee_id) AS unique_employees
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
GROUP BY av.status
ORDER BY 
    CASE av.status
        WHEN 'PENDING' THEN 1
        WHEN 'IN_PROGRESS' THEN 2
        WHEN 'APPROVED' THEN 3
        WHEN 'AUTO_APPROVED' THEN 4
        WHEN 'REJECTED' THEN 5
    END;

-- CHI TIẾT VERIFICATION ĐANG CHỜ XỬ LÝ
SELECT 
    '=== VERIFICATION ĐANG CHỜ XỬ LÝ ===' AS title;

SELECT 
    av.id AS verification_id,
    e.employee_code,
    e.name AS employee_name,
    c.description AS contract_description,
    av.reason,
    av.status,
    av.current_attempts,
    av.max_attempts,
    COUNT(vi.id) AS images_captured,
    CASE 
        WHEN av.current_attempts >= av.max_attempts THEN 'Đủ điều kiện tự động duyệt'
        WHEN av.current_attempts > 0 THEN 'Đang chờ duyệt'
        ELSE 'Chưa chụp ảnh'
    END AS action_needed,
    av.created_at
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
INNER JOIN contracts c ON a.contract_id = c.id
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
WHERE av.status IN ('PENDING', 'IN_PROGRESS')
GROUP BY av.id, e.employee_code, e.name, c.description, 
         av.reason, av.status, av.current_attempts, av.max_attempts, av.created_at
ORDER BY av.created_at DESC;

-- VERIFICATION ĐÃ HOÀN THÀNH
SELECT 
    '=== VERIFICATION ĐÃ HOÀN THÀNH ===' AS title;

SELECT 
    av.id AS verification_id,
    e.employee_code,
    e.name AS employee_name,
    av.status,
    av.current_attempts,
    CASE 
        WHEN av.status = 'APPROVED' THEN u.username
        ELSE 'Hệ thống'
    END AS approved_by,
    COALESCE(av.approved_at, av.auto_approved_at) AS approved_at,
    COUNT(vi.id) AS total_images
FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
INNER JOIN employees e ON a.employee_id = e.id
LEFT JOIN users u ON av.approved_by_id = u.id
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
WHERE av.status IN ('APPROVED', 'AUTO_APPROVED')
GROUP BY av.id, e.employee_code, e.name, av.status, 
         av.current_attempts, u.username, av.approved_at, av.auto_approved_at
ORDER BY COALESCE(av.approved_at, av.auto_approved_at) DESC
LIMIT 20;

-- THỐNG KÊ THEO NHÂN VIÊN
SELECT 
    '=== THỐNG KÊ THEO NHÂN VIÊN ===' AS title;

SELECT 
    e.employee_code,
    e.name AS employee_name,
    COUNT(DISTINCT av.id) AS total_verifications,
    SUM(CASE WHEN av.status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
    SUM(CASE WHEN av.status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress,
    SUM(CASE WHEN av.status = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
    SUM(CASE WHEN av.status = 'AUTO_APPROVED' THEN 1 ELSE 0 END) AS auto_approved,
    COUNT(DISTINCT vi.id) AS total_images_captured
FROM employees e
LEFT JOIN assignments a ON e.id = a.employee_id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
LEFT JOIN verification_images vi ON av.id = vi.assignment_verification_id
GROUP BY e.id, e.employee_code, e.name
HAVING total_verifications > 0
ORDER BY total_verifications DESC;

-- KIỂM TRA NHÂN VIÊN MỚI
SELECT 
    '=== NHÂN VIÊN MỚI (Chưa có verification hoàn thành) ===' AS title;

SELECT 
    e.employee_code,
    e.name AS employee_name,
    e.phone,
    COUNT(DISTINCT a.id) AS total_assignments,
    COUNT(DISTINCT av.id) AS total_verifications,
    SUM(CASE WHEN av.status IN ('APPROVED', 'AUTO_APPROVED') THEN 1 ELSE 0 END) AS completed_verifications,
    CASE 
        WHEN SUM(CASE WHEN av.status IN ('APPROVED', 'AUTO_APPROVED') THEN 1 ELSE 0 END) = 0 
        THEN 'NHÂN VIÊN MỚI'
        ELSE 'Nhân viên cũ'
    END AS employee_type
FROM employees e
LEFT JOIN assignments a ON e.id = a.employee_id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
GROUP BY e.id, e.employee_code, e.name, e.phone
HAVING total_assignments > 0
ORDER BY completed_verifications, e.employee_code;

-- ============================================================================
-- HƯỚNG DẪN SỬ DỤNG:
-- 
-- 1. Chạy script này để xem tổng quan hệ thống
-- 2. Kiểm tra verification nào cần xử lý
-- 3. Kiểm tra nhân viên nào là "mới" (chưa có verification hoàn thành)
-- ============================================================================
