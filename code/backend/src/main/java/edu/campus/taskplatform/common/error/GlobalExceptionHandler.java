package edu.campus.taskplatform.common.error;

import java.util.stream.Collectors;

import edu.campus.taskplatform.common.api.ApiResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/**
 * 把可预期异常转换为统一错误响应；未预期异常不向外暴露堆栈。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception) {
		return response(exception.errorCode(), exception.getMessage());
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
	public ResponseEntity<ApiResponse<Void>> handleBindingException(BindException exception) {
		String detail = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.distinct()
				.collect(Collectors.joining("; "));
		return response(ErrorCode.VALIDATION_ERROR,
				detail.isBlank() ? ErrorCode.VALIDATION_ERROR.defaultMessage() : detail);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
		return response(ErrorCode.VALIDATION_ERROR, exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return response(ErrorCode.VALIDATION_ERROR, "参数 " + exception.getName() + " 格式不正确");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
		return response(ErrorCode.VALIDATION_ERROR, "请求体格式不正确");
	}

	/**
	 * 业务唯一约束（如同一任务同时只允许一条待处理取消申请）由数据库兜底，
	 * 并发写入穿透服务层检查时转换为 409。
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
		return response(ErrorCode.DUPLICATE_ACTION, ErrorCode.DUPLICATE_ACTION.defaultMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
		return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
	}

	private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.status())
				.body(ApiResponse.error(errorCode.name(), message));
	}
}
