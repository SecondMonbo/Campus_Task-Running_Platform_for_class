package edu.campus.taskplatform.dispute.cancellation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发起取消申请请求体，对应 OpenAPI {@code CreateCancellationRequest}。
 */
public record CreateCancellationRequest(
		@NotBlank(message = "取消原因不能为空")
		@Size(max = 200, message = "取消原因不能超过 200 个字符")
		String reason) {
}
