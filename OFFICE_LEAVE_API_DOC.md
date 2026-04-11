# Tài liệu API: Quản lý Lịch Làm Việc Nhân viên Văn Phòng (Dành cho FE)

Dưới đây là danh sách các API backend mới được cập nhật để Frontend (FE) triển khai quy trình điều chỉnh lịch làm việc/nghỉ phép dành riêng cho **Nhân viên khối văn phòng (COMPANY_STAFF)** hoặc các nhân viên có lịch làm việc cố định theo công ty (`AssignmentType.FIXED_BY_COMPANY`).

> Chú ý: Tất cả các API này đều yêu cầu quyền `EMPLOYEE_EDIT` và được gửi kèm token xác thực. Hệ thống KHÔNG làm thay đổi trạng thái đăng nhập chung của `Employee` trên toàn hệ thống (tức là họ vẫn làm hợp đồng khách được), mà chỉ tác động tới chấm công của văn phòng.

---

## 1. Xin nghỉ phép 1 ngày (Take Company Leave)
Sử dụng khi nhân viên xin nghỉ một ngày cụ thể. Hành động này sẽ loại bỏ dữ liệu tính công của riêng ngày đó rên hệ thống tính lương cuối tháng, mà không ảnh hưởng tới các ngày khác của nhân viên.

* **Endpoint:** `PUT /api/employees/{id}/company-leave`
* **Mô tả:** Đăng ký nghỉ việc không hưởng lương tại văn phòng trong một ngày cụ thể. 
* **URL Params:**
  * `{id}` (Long): ID của nhân viên (`Employee.id`).
* **Query Params:**
  * `leaveDate` (String - Format `YYYY-MM-DD`): Ngày xin nghỉ làm.
    * *Ví dụ: `?leaveDate=2026-03-24`*
* **Response (Thành công - 200 OK):**
```json
{
  "message": "Đã ghi nhận nhân viên nghỉ 1 ngày",
  "data": {
    "id": 1,
    "employeeCode": "NVVP000001",
    "name": "Nguyễn Văn A"
    // Trả về toàn bộ EmployeeResponse
  },
  "status": 200
}
```

---

## 2. Huỷ nghỉ phép/Khôi phục ca làm (Cancel Company Leave)
Sử dụng khi một ngày nghỉ của nhân viên trước đó bị đánh dấu nhầm hoặc nhân viên quyết định đi làm lại vào ngày đã xin nghỉ.

* **Endpoint:** `PUT /api/employees/{id}/cancel-company-leave`
* **Mô tả:** Huỷ bỏ "Ngày nghỉ" đã đăng ký trước đó. Khôi phục lại dữ liệu chấm công của ngày này để trả lương bình thường.
* **URL Params:**
  * `{id}` (Long): ID của nhân viên (`Employee.id`).
* **Query Params:**
  * `leaveDate` (String - Format `YYYY-MM-DD`): Ngày muốn huỷ thao tác nghỉ.
    * *Ví dụ: `?leaveDate=2026-03-24`*
* **Response (Thành công - 200 OK):**
```json
{
  "message": "Đã hủy ngày nghỉ của nhân viên",
  "data": { },
  "status": 200
}
```

---

## 3. Chốt nghỉ hẳn công việc văn phòng (Resign Office Work)
Dùng trong trường hợp thủ tục nghỉ việc chính thức/dài hạn cho lịch hành chính trên công ty.

* **Endpoint:** `PUT /api/employees/{id}/resign-office`
* **Mô tả:** Điền ngày nhân viên chính thức ngừng làm việc văn phòng. Phía backend sẽ đánh dấu hợp đồng văn phòng thành `TERMINATED`, ngắt toàn bộ tự động sinh bảng công cho các ngày tương lai (`>= resignDate`).
* **URL Params:**
  * `{id}` (Long): ID của nhân viên (`Employee.id`).
* **Query Params:**
  * `resignDate` (String - Format `YYYY-MM-DD`): Ngày bắt đầu chính thức chốt nghỉ làm văn phòng.
    * *Ví dụ: `?resignDate=2026-03-31`*
* **Response (Thành công - 200 OK):**
```json
{
  "message": "Đã thiết lập nghỉ hẳn làm việc văn phòng",
  "data": { },
  "status": 200
}
```

---

### Hướng dẫn thao tác cho UI (FE Guidance)
1. **Quản lý Nghỉ phép (1 ngày hoặc vài ngày):** Hiển thị màn hình Lịch (Calendar). Ấn vào ngày nào, gọi API 1 (`/company-leave`). Ấn lại vào ngày đó để Bỏ chọn, gọi API 2 (`/cancel-company-leave`). 
2. **Chấm dứt hẳn văn phòng:** Thêm một Nút báo động "Báo Nghỉ Hẳn" ở trang Chi tiết Nhân sự Văn phòng. Hiện Modal bắt Confirm (chọn ngày `resignDate`), xong gọi API 3.
3. Không bắt buộc gửi Content-Type JSON vì data thao tác chỉ dựa trên Query String!
