package com.company.company_clean_hub_be.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    USER_IS_EXISTS(400,"Người dùng đã tồn tại"),
    USER_IS_NOT_EXISTS(400,"Người dùng không tồn tại"),
    USERNAME_OR_PASSWORD_VALID(969,"Username or password is valid!"),
    EMAIL_IS_EXISTS(400,"Email đã tồn tại"),
    VERIFY_CODE_VALID(400,"Mã xác nhận không chính xác, vui lòng kiểm tra lại trong email!!"),
    LOGIN_VALID(400,"Tên đăng nhập hoặc mật khẩu không chính xác!"),
    PASS_NOT_MATCH(400,"Mật khẩu cũ không chính xác!"),
    VALID_TOKEN(401,"Token không chính xác hoặc đã hết hạn sử dụng!"),
    EMPLOYEE_NOT_FOUND(404,"Nhân viên không tồn tại"),
    CUSTOMER_NOT_FOUND(404,"Khách hàng không tồn tại"),
    CONTRACT_NOT_FOUND(404,"Hợp đồng không tồn tại"),
    SERVICE_NOT_FOUND(404,"Dịch vụ không tồn tại"),
    ASSIGNMENT_NOT_FOUND(404,"Phân công không tồn tại"),
    ROLE_NOT_FOUND(404,"Vai trò không tồn tại"),
    IMAGE_NOT_FOUND(404, "Ảnh nhân viên không tồn tại"),
    
    USERNAME_ALREADY_EXISTS(400,"Tên đăng nhập đã tồn tại"),
    PHONE_ALREADY_EXISTS(400,"Số điện thoại đã tồn tại"),
    EMPLOYEE_CODE_ALREADY_EXISTS(400,"Mã nhân viên đã tồn tại"),
    CCCD_ALREADY_EXISTS(400,"Số CCCD đã tồn tại"),
    BANK_ACCOUNT_ALREADY_EXISTS(400,"Số tài khoản ngân hàng đã tồn tại"),
    PAYROLL_NOT_FOUND(404,"Bảng lương không tồn tại"),
    PAYROLL_ALREADY_EXISTS(400,"Bảng lương tháng này đã tồn tại"),
    ATTENDANCE_NOT_FOUND(404,"Chấm công không tồn tại"),
    ATTENDANCE_ALREADY_EXISTS(400,"Chấm công ngày này đã tồn tại"),

    UNAUTHENTICATED(403,"Không có quyền truy cập" );

    private final int code;
    private final String message;
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
