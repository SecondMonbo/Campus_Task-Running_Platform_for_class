package edu.campus.taskplatform.common;

import org.springframework.http.HttpStatus;

/**
 * 稳定错误码，与 openapi.yaml 中 ErrorResponse / 各 response 示例保持一致。
 * 前端只根据 HTTP 状态码和 code 展示提示，不解析服务端堆栈。
 */
public enum ErrorCode {

    /** 成功。 */
    OK(HttpStatus.OK),
    /** 请求格式、参数或业务输入无效。 */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    /** 未登录或会话已失效。 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    /** 已登录但无权执行此操作。 */
    FORBIDDEN(HttpStatus.FORBIDDEN),
    /** 资源不存在或当前用户不可见。 */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** 任务/申请状态冲突或并发操作失败。 */
    TASK_STATE_CONFLICT(HttpStatus.CONFLICT),
    /** 余额不足。 */
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT),
    /** 重复业务操作。 */
    DUPLICATE_ACTION(HttpStatus.CONFLICT),
    /** 未预期的服务端错误。 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String code() {
        return name();
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
