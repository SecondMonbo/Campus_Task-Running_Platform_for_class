package edu.campus.taskplatform.common.api;

/**
 * 统一响应结构，字段与《API约定.md》第 3 节一致。
 *
 * <p>该类型属于跨模块公共能力。组长实现统一响应后，可由 {@code common} 包整体替换，
 * 本模块只依赖其结构，不依赖其实现。</p>
 */
public record ApiResponse<T>(String code, String message, T data) {

	public static final String SUCCESS_CODE = "OK";
	public static final String SUCCESS_MESSAGE = "success";

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
	}

	public static ApiResponse<Void> error(String code, String message) {
		return new ApiResponse<>(code, message, null);
	}
}
