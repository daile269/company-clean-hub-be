# 📋 HƯỚNG DẪN SỬ DỤNG TEST SCRIPTS

Bộ script SQL để test tính năng xác minh hình ảnh và chấm công.

---

## 📁 DANH SÁCH SCRIPTS

### 1️⃣ `01_reset_employee_history.sql`
**Mục đích:** Xóa sạch lịch sử của 1 nhân viên để test như nhân viên mới

**Cách dùng:**
```sql
-- Sửa mã nhân viên
SET @employee_code = 'NV001';

-- Chạy toàn bộ script
```

**Kết quả:**
- Xóa tất cả: assignments, attendances, verifications, images
- Nhân viên trở về trạng thái "mới"

---

### 2️⃣ `02_create_test_scenario_new_employee.sql`
**Mục đích:** Tạo kịch bản test cho nhân viên mới

**Cách dùng:**
```sql
-- Sửa thông tin
SET @employee_code = 'NV001';
SET @contract_id = 1;

-- Chạy toàn bộ script
```

**Kết quả:**
- Tạo 1 assignment mới
- Tạo verification với reason = NEW_EMPLOYEE
- Tạo 1 attendance cho ngày đầu tiên
- Nhân viên có thể chụp ảnh

**Test tiếp theo:**
1. Login với mã NV001
2. Vào trang "Chụp ảnh chấm công"
3. Chụp ảnh
4. Quản lý vào `/admin/verifications` để duyệt

---

### 3️⃣ `03_create_test_scenario_contract_verification.sql`
**Mục đích:** Test hợp đồng yêu cầu xác minh

**Cách dùng:**
```sql
-- Sửa thông tin
SET @employee_code = 'NV002';  -- Nhân viên CŨ
SET @contract_id = 2;

-- Chạy toàn bộ script
```

**Kết quả:**
- Bật cờ `requires_image_verification = TRUE` cho hợp đồng
- Nhân viên CŨ vẫn phải xác minh
- Verification reason = CONTRACT_SETTING

---

### 4️⃣ `04_simulate_image_capture.sql`
**Mục đích:** Tạo ảnh xác minh giả để test

**Cách dùng:**
```sql
-- Sửa ID
SET @verification_id = 1;
SET @attendance_id = 1;

-- Chạy toàn bộ script
```

**Kết quả:**
- Tạo 1 ảnh xác minh với GPS, thời gian
- Cập nhật current_attempts + 1
- Status chuyển sang IN_PROGRESS

**Lưu ý:** Trong thực tế, ảnh được upload qua API, không dùng SQL

---

### 5️⃣ `05_test_auto_approval.sql`
**Mục đích:** Tạo verification đã chụp đủ 5 lần

**Cách dùng:**
```sql
-- Sửa ID
SET @verification_id = 1;

-- Chạy toàn bộ script
```

**Kết quả:**
- Tạo 5 ảnh (mỗi ngày 1 ảnh)
- current_attempts = 5
- Đủ điều kiện auto-approve

**Test tiếp theo:**
- Chạy script 6 để auto-approve thủ công
- Hoặc đợi scheduled job chạy (1h, 7h, 13h, 19h)

---

### 6️⃣ `06_manual_auto_approval.sql`
**Mục đích:** Thực hiện auto-approval thủ công

**Cách dùng:**
```sql
-- Chạy toàn bộ script (không cần sửa gì)
```

**Kết quả:**
- Tìm tất cả verification đủ điều kiện
- Cập nhật status = AUTO_APPROVED
- Hiển thị danh sách đã auto-approve

**Lưu ý:** Cần chạy script 7 để sinh attendance còn lại

---

### 7️⃣ `07_generate_remaining_attendances.sql`
**Mục đích:** Sinh attendance còn lại sau khi duyệt

**Cách dùng:**
```sql
-- Sửa ID
SET @assignment_id = 1;

-- Chạy toàn bộ script
```

**Kết quả:**
- Sinh attendance từ ngày sau ngày chụp ảnh cuối
- Không sinh Chủ nhật
- Cập nhật work_days

**Logic:**
```
Ngày chụp ảnh cuối: 05/03/2026
Sinh attendance từ: 06/03/2026 đến cuối tháng
```

---

### 8️⃣ `08_check_verification_status.sql`
**Mục đích:** Xem tổng quan hệ thống

**Cách dùng:**
```sql
-- Chạy toàn bộ script (không cần sửa gì)
```

**Kết quả:**
- Tổng quan verification theo status
- Danh sách verification đang chờ
- Thống kê theo nhân viên
- Danh sách nhân viên mới

---

## 🎯 KỊCH BẢN TEST HOÀN CHỈNH

### Kịch bản 1: Test nhân viên mới
```bash
1. Chạy script 01 - Reset nhân viên NV001
2. Chạy script 02 - Tạo assignment cho NV001
3. Login NV001, chụp ảnh qua UI
4. Quản lý vào /admin/verifications, duyệt
5. Chạy script 08 - Kiểm tra kết quả
```

### Kịch bản 2: Test hợp đồng yêu cầu xác minh
```bash
1. Chạy script 03 - Tạo assignment với contract verification
2. Login nhân viên, chụp ảnh
3. Quản lý duyệt
4. Kiểm tra attendance đã sinh đầy đủ
```

### Kịch bản 3: Test auto-approval
```bash
1. Chạy script 02 - Tạo assignment
2. Chạy script 05 - Tạo 5 ảnh
3. Chạy script 06 - Auto-approve thủ công
4. Chạy script 07 - Sinh attendance còn lại
5. Chạy script 08 - Kiểm tra kết quả
```

### Kịch bản 4: Test giới hạn 1 ảnh/ngày
```bash
1. Chạy script 02 - Tạo assignment
2. Login nhân viên, chụp ảnh lần 1 → Thành công
3. Thử chụp lại trong ngày → Bị chặn "Đã chụp hôm nay"
4. Đợi sang ngày mới, chụp lại → Thành công
```

---

## ⚠️ LƯU Ý QUAN TRỌNG

### 1. Thứ tự chạy script
- Luôn chạy script 01 trước khi test nhân viên mới
- Script 07 phải chạy sau script 06
- Script 08 có thể chạy bất cứ lúc nào

### 2. Dữ liệu test
- Các script tạo dữ liệu GIẢ (test data)
- Không dùng trên production
- Ảnh Cloudinary là URL giả, không tồn tại thật

### 3. Backend logic
- Một số logic chỉ chạy trong backend (Java)
- Script SQL chỉ mô phỏng, không thay thế hoàn toàn
- Nên test qua UI để đảm bảo đầy đủ

### 4. Scheduled job
- Auto-approval chạy tự động: 1h, 7h, 13h, 19h
- Có thể test thủ công bằng script 06
- Hoặc gọi API: `POST /api/verifications/process-auto-approvals`

---

## 🔍 KIỂM TRA KẾT QUẢ

### Kiểm tra verification
```sql
SELECT * FROM assignment_verifications WHERE id = 1;
```

### Kiểm tra ảnh
```sql
SELECT * FROM verification_images WHERE assignment_verification_id = 1;
```

### Kiểm tra attendance
```sql
SELECT * FROM attendances WHERE assignment_id = 1 ORDER BY date;
```

### Kiểm tra nhân viên mới
```sql
SELECT 
    e.employee_code,
    COUNT(DISTINCT av.id) AS total_verifications,
    SUM(CASE WHEN av.status IN ('APPROVED', 'AUTO_APPROVED') THEN 1 ELSE 0 END) AS completed
FROM employees e
LEFT JOIN assignments a ON e.id = a.employee_id
LEFT JOIN assignment_verifications av ON a.id = av.assignment_id
WHERE e.employee_code = 'NV001'
GROUP BY e.employee_code;
```

---

## 📞 HỖ TRỢ

Nếu gặp vấn đề:
1. Kiểm tra log backend
2. Chạy script 08 để xem tổng quan
3. Kiểm tra foreign key constraints
4. Đảm bảo employee_code và contract_id tồn tại

---

**Tác giả:** Kiro AI Assistant  
**Ngày tạo:** 14/03/2026  
**Phiên bản:** 1.0
