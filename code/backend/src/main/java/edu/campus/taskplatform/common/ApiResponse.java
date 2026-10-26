package edu.campus.taskplatform.common;

/**
 * 统一响应体，契约见 openapi.yaml 的 ApiResponse / ErrorResponse。
 *
 * <pre>
 * { "code": "OK", "message": "success", "data": ... }
 * { "code": "TASK_STATE_CONFLICT", "message": "...", "data": null }
 * </pre>
 */
public final class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), "success", data);
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.code(), message, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
