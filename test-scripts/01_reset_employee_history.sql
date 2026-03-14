-- ============================================================================
-- SCRIPT 1: XÓA SẠCH LỊCH SỬ NHÂN VIÊN
-- ============================================================================
-- Mục đích: Xóa toàn bộ lịch sử phân công, xác minh, attendance của nhân viên
-- Kết quả: Nhân viên như chưa từng đi làm, sẽ được coi là "nhân viên mới"
-- ============================================================================

-- QUAN TRỌNG: Chọn database
USE company_clean_hub;

-- BƯỚC 0: Thiết lập
SET @employee_code = 'NV000049';  -- Thay bằng mã nhân viên thực tế

-- BƯỚC 1: Tìm employee_id
SET @employee_id = (SELECT id FROM employees WHERE employee_code = @employee_code);

-- Kiểm tra nhân viên có tồn tại không
SELECT 
    CASE 
        WHEN @employee_id IS NULL THEN 'ERROR: Không tìm thấy nhân viên với mã này!'
        ELSE CONCAT('✓ Tìm thấy nhân viên: ID = ', @employee_id, ', Mã = ', @employee_code)
    END AS status;

-- BƯỚC 2: Xóa ảnh xác minh của nhân viên
DELETE FROM verification_images 
WHERE employee_id = @employee_id;

SELECT CONCAT('✓ Đã xóa ', ROW_COUNT(), ' ảnh xác minh') AS result;

-- BƯỚC 3: Xóa yêu cầu xác minh của nhân viên
DELETE av FROM assignment_verifications av
INNER JOIN assignments a ON av.assignment_id = a.id
WHERE a.employee_id = @employee_id;

SELECT CONCAT('✓ Đã xóa ', ROW_COUNT(), ' yêu cầu xác minh') AS result;

-- BƯỚC 4: Xóa đánh giá của nhân viên
DELETE e FROM evaluations e
INNER JOIN attendance att ON e.attendance_id = att.id
INNER JOIN assignments a ON att.assignment_id = a.id
WHERE a.employee_id = @employee_id;

SELECT CONCAT('✓ Đã xóa ', ROW_COUNT(), ' đánh giá') AS result;

-- BƯỚC 5: Xóa attendance của nhân viên
DELETE att FROM attendance att
INNER JOIN assignments a ON att.assignment_id = a.id
WHERE a.employee_id = @employee_id;

SELECT CONCAT('✓ Đã xóa ', ROW_COUNT(), ' bản ghi attendance') AS result;

-- BƯỚC 6: Xóa phân công của nhân viên
DELETE FROM assignments 
WHERE employee_id = @employee_id;

SELECT CONCAT('✓ Đã xóa ', ROW_COUNT(), ' phân công') AS result;

-- BƯỚC 7: Kiểm tra kết quả
SELECT 
    '=== KẾT QUẢ CUỐI CÙNG ===' AS title;

SELECT 
    @employee_code AS employee_code,
    @employee_id AS employee_id,
    (SELECT COUNT(*) FROM assignments WHERE employee_id = @employee_id) AS assignments_count,
    (SELECT COUNT(*) FROM attendance att 
     INNER JOIN assignments a ON att.assignment_id = a.id 
     WHERE a.employee_id = @employee_id) AS attendances_count,
    (SELECT COUNT(*) FROM assignment_verifications av
     INNER JOIN assignments a ON av.assignment_id = a.id
     WHERE a.employee_id = @employee_id) AS verifications_count,
    (SELECT COUNT(*) FROM verification_images WHERE employee_id = @employee_id) AS images_count,
    '✓ Nhân viên đã được reset về trạng thái mới!' AS status;

-- ============================================================================
-- KẾT QUẢ MONG ĐỢI:
-- - assignments_count = 0
-- - attendances_count = 0
-- - verifications_count = 0
-- - images_count = 0
-- ============================================================================
