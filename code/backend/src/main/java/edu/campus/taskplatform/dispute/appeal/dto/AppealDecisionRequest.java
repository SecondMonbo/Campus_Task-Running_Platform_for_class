package edu.campus.taskplatform.dispute.appeal.dto;

import edu.campus.taskplatform.dispute.appeal.AppealResult;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员裁决申诉请求体，对应 OpenAPI {@code AppealDecisionRequest}。
 */
public record AppealDecisionRequest(
		@NotNull(message = "result 不能为空")
		AppealResult result,

		@NotNull(message = "transferAmount 不能为空")
		@Min(value = 0, message = "transferAmount 不能为负数")
		Integer transferAmount,

		@NotBlank(message = "裁决说明不能为空")
		@Size(max = 500, message = "裁决说明不能超过 500 个字符")
		String decisionNote) {
}
