package com.company.company_clean_hub_be.exception;

public class AppException extends RuntimeException{

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = null;
    }
    public AppException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
        this.customMessage = null;
    }
    /** Constructor với message động (thêm chi tiết giờ làm, v.v.) */
    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
        this.data = null;
    }

    private ErrorCode errorCode;
    private Object data;
    private String customMessage;

    public ErrorCode getErrorCode(){
        return errorCode;
    }
    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }
    public Object getData() {
        return data;
    }
    /** Trả về message tùy chỉnh nếu có, ngược lại trả message từ ErrorCode */
    public String getResolvedMessage() {
        return customMessage != null ? customMessage : errorCode.getMessage();
    }
}
