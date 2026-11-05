package edu.campus.taskplatform.common.error;

import org.springframework.http.HttpStatus;

/**
 * 稳定错误码。前端按 HTTP 状态码与 {@code code} 展示提示，不解析服务端消息与堆栈。
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数不合法"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "请先登录"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行此操作"),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "未找到对应资源"),

	TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "任务不存在或当前用户不可见"),
	CANCELLATION_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "取消申请不存在或当前用户不可见"),
	APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "申诉不存在或当前用户不可见"),

	TASK_STATE_CONFLICT(HttpStatus.CONFLICT, "任务当前状态不允许此操作"),
	CANCELLATION_STATE_CONFLICT(HttpStatus.CONFLICT, "取消申请当前状态不允许此操作"),
	CANCELLATION_ALREADY_PENDING(HttpStatus.CONFLICT, "该任务已有待处理的取消申请"),
	APPEAL_ALREADY_EXISTS(HttpStatus.CONFLICT, "该参与者已对同一任务提交过申诉"),
	APPEAL_ALREADY_DECIDED(HttpStatus.CONFLICT, "该申诉已裁决，不能重复裁决"),
	INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "被申诉方可用余额不足"),
	DUPLICATE_ACTION(HttpStatus.CONFLICT, "重复的业务操作"),

	INTEGRATION_NOT_READY(HttpStatus.INTERNAL_SERVER_ERROR, "依赖的公共能力尚未接入"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
