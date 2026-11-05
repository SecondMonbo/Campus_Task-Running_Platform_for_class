package edu.campus.taskplatform.dispute.appeal.dto;

import edu.campus.taskplatform.dispute.appeal.AppealReason;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 提交已完成任务申诉请求体，对应 OpenAPI {@code CreateAppealRequest}。
 */
public record CreateAppealRequest(
		@NotNull(message = "reason 不能为空")
		AppealReason reason,

		@NotBlank(message = "申诉说明不能为空")
		@Size(max = 500, message = "申诉说明不能超过 500 个字符")
		String description) {
}
