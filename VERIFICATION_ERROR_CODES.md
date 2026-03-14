# CÁC ERROR CODE CẦN BỔ SUNG CHO VERIFICATION

Cần thêm các ErrorCode sau vào enum ErrorCode:

```java
// Verification related errors
VERIFICATION_NOT_FOUND(404, "Không tìm thấy yêu cầu xác minh"),
VERIFICATION_CAPTURE_NOT_ALLOWED(400, "Không thể chụp ảnh xác minh (đã hoàn thành hoặc hết lượt)"),
VERIFICATION_ALREADY_APPROVED(400, "Yêu cầu xác minh đã được duyệt"),
VERIFICATION_ALREADY_EXISTS(400, "Yêu cầu xác minh đã tồn tại cho assignment này"),

// File upload errors  
FILE_UPLOAD_FAILED(500, "Tải lên file thất bại"),
INVALID_IMAGE_FORMAT(400, "Định dạng ảnh không hợp lệ"),
IMAGE_TOO_LARGE(400, "Kích thước ảnh quá lớn"),

// Attendance generation errors
INVALID_ATTENDANCE_DATE(400, "Ngày chấm công không hợp lệ"),
ATTENDANCE_GENERATION_FAILED(500, "Sinh chấm công thất bại"),

// Assignment errors
ASSIGNMENT_VERIFICATION_REQUIRED(400, "Assignment này yêu cầu xác minh hình ảnh"),
```

## Vị trí thêm
File: `src/main/java/com/company/company_clean_hub_be/exception/ErrorCode.java`