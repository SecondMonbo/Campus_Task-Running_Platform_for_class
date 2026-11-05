package edu.campus.taskplatform.dispute.cancellation.dto;

import edu.campus.taskplatform.dispute.cancellation.CancellationDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 取消申请应答请求体，对应 OpenAPI {@code CancellationResponseRequest}。
 */
public record CancellationResponseRequest(
		@NotNull(message = "decision 不能为空")
		CancellationDecision decision,

		@Size(max = 500, message = "说明不能超过 500 个字符")
		String note) {
}
