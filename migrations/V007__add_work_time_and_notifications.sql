-- ============================================================
-- SQL Migration: Thêm cột khung giờ vào bảng contracts
--                Thêm bảng notifications
--                Thêm quyền NOTIFICATION_VIEW, NOTIFICATION_MANAGE cho role QLT
-- ============================================================

-- ─── 1. Thêm các cột mới vào bảng contracts ────────────────
ALTER TABLE contracts
    ADD COLUMN number_of_employees INT          NULL COMMENT 'Số lượng nhân viên phụ trách hợp đồng',
    ADD COLUMN work_start_time     TIME         NULL COMMENT 'Giờ bắt đầu làm việc (VD: 07:00)',
    ADD COLUMN work_end_time       TIME         NULL COMMENT 'Giờ kết thúc làm việc (VD: 09:00)';

-- ─── 2. Tạo bảng notifications ─────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id     BIGINT       NOT NULL COMMENT 'User nhận thông báo (QLT)',
    type             VARCHAR(50)  NOT NULL COMMENT 'WORK_TIME_CONFLICT | NEW_EMPLOYEE_CREATED',
    title            VARCHAR(255) NULL,
    message          TEXT         NULL,
    ref_employee_id  BIGINT       NULL COMMENT 'ID nhân viên liên quan',
    ref_assignment_id BIGINT      NULL COMMENT 'ID phân công liên quan',
    ref_contract_id  BIGINT       NULL COMMENT 'ID hợp đồng liên quan',
    is_read          TINYINT(1)   NOT NULL DEFAULT 0,
    created_at       DATETIME     NULL,
    PRIMARY KEY (id),
    INDEX idx_notifications_recipient (recipient_id),
    INDEX idx_notifications_is_read   (recipient_id, is_read),
    CONSTRAINT fk_notifications_recipient
        FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Bảng lưu thông báo cho Quản lý tổng';

-- ─── 3. Mở rộng ENUM permission để thêm 2 giá trị mới ──────
-- (Bắt buộc vì cột permission là ENUM, không phải VARCHAR)
ALTER TABLE role_permissions
MODIFY COLUMN permission ENUM(
'APPROVE_PROFILE_CHANGE',
'ASSIGNMENT_CREATE','ASSIGNMENT_DELETE','ASSIGNMENT_REASSIGN','ASSIGNMENT_UPDATE','ASSIGNMENT_VIEW',
'ATTENDANCE_CREATE','ATTENDANCE_DELETE','ATTENDANCE_EDIT','ATTENDANCE_EXPORT','ATTENDANCE_VIEW',
'AUDIT_VIEW',
'CONTRACT_CREATE','CONTRACT_DELETE','CONTRACT_EDIT','CONTRACT_VIEW',
'COST_MANAGE',
'CUSTOMER_ASSIGN','CUSTOMER_CREATE','CUSTOMER_DELETE','CUSTOMER_EDIT','CUSTOMER_VIEW',
'EMPLOYEE_CREATE','EMPLOYEE_DELETE','EMPLOYEE_EDIT','EMPLOYEE_VIEW','EMPLOYEE_VIEW_OWN',
'INVOICE_CREATE','INVOICE_DELETE','INVOICE_EDIT','INVOICE_EXPORT','INVOICE_VIEW',
'PAYROLL_ADVANCE','PAYROLL_CREATE','PAYROLL_EDIT','PAYROLL_EXPORT','PAYROLL_MARK_PAID','PAYROLL_VIEW',
'REQUEST_PROFILE_CHANGE',
'REVIEW_CREATE','REVIEW_DELETE','REVIEW_UPDATE','REVIEW_VIEW_ALL','REVIEW_VIEW_CONTRACT',
'SERVICE_CREATE','SERVICE_DELETE','SERVICE_EDIT','SERVICE_EXPORT','SERVICE_VIEW',
'USER_CREATE','USER_DELETE','USER_EDIT','USER_MANAGE_ALL','USER_VIEW',
'NOTIFICATION_VIEW',
'NOTIFICATION_MANAGE'
) DEFAULT NULL;

-- ─── 4. Thêm quyền NOTIFICATION cho role QLT (role_id = 3) ─
INSERT IGNORE INTO role_permissions (role_id, permission) VALUES
    (3, 'NOTIFICATION_VIEW'),
    (3, 'NOTIFICATION_MANAGE');

-- ─── Verify ────────────────────────────────────────────────
-- Kiểm tra cột đã được thêm vào contracts
-- SHOW COLUMNS FROM contracts LIKE 'work_%';

-- Kiểm tra bảng notifications đã được tạo
-- SHOW TABLES LIKE 'notifications';

-- Kiểm tra quyền đã được thêm cho QLT
-- SELECT r.code, rp.permission
-- FROM role_permissions rp
-- JOIN roles r ON r.id = rp.role_id
-- WHERE r.code = 'QLT' AND rp.permission LIKE 'NOTIFICATION%';
