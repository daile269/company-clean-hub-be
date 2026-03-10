-- ============================================================
-- Fix charset bảng notifications: đổi title và message sang utf8mb4
-- để hỗ trợ lưu emoji (👤 ⚠️ v.v.)
-- ============================================================

ALTER TABLE notifications
    MODIFY COLUMN title   VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    MODIFY COLUMN message TEXT         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

-- Đảm bảo cả bảng dùng utf8mb4
ALTER TABLE notifications
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
