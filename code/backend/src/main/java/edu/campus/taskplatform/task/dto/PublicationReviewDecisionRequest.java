package edu.campus.taskplatform.task.dto;

import edu.campus.taskplatform.task.PublicationReviewDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 管理员处理发布复核：批准后任务公开，驳回后任务转为已取消并解冻报酬。 */
public record PublicationReviewDecisionRequest(
		@NotNull(message = "decision 不能为空")
		PublicationReviewDecision decision,

		@NotBlank(message = "复核说明不能为空")
		@Size(max = 500, message = "复核说明不能超过 500 个字符")
		String note) {
}
