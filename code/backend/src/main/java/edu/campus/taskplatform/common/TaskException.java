package edu.campus.taskplatform.common;

/**
 * 业务异常：携带稳定错误码。GlobalExceptionHandler 据此返回统一响应体。
 */
public class TaskException extends RuntimeException {

    private final ErrorCode errorCode;

    public TaskException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
