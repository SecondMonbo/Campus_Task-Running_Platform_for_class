package edu.campus.taskplatform.dispute.cancellation.dto;

import edu.campus.taskplatform.dispute.cancellation.CancellationRuling;
import edu.campus.taskplatform.dispute.cancellation.ResponsibleParty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员裁决取消请求体，对应 OpenAPI {@code CancellationArbitrationRequest}。
 * 责任方必须与裁决事实一致；支持继续履约时必须为 NONE。
 */
public record CancellationArbitrationRequest(
		@NotNull(message = "ruling 不能为空")
		CancellationRuling ruling,

		@NotNull(message = "refundAmount 不能为空")
		@Min(value = 0, message = "refundAmount 不能为负数")
		Integer refundAmount,

		@NotNull(message = "runnerAmount 不能为空")
		@Min(value = 0, message = "runnerAmount 不能为负数")
		Integer runnerAmount,

		@NotNull(message = "responsibleParty 不能为空")
		ResponsibleParty responsibleParty,

		@NotBlank(message = "裁决说明不能为空")
		@Size(max = 500, message = "裁决说明不能超过 500 个字符")
		String rulingDetail) {

	/** 兼容旧调用：未明确责任方时按 NONE 处理，接口层仍要求显式传入。 */
	public CancellationArbitrationRequest(CancellationRuling ruling, Integer refundAmount,
			Integer runnerAmount, String rulingDetail) {
		this(ruling, refundAmount, runnerAmount, ResponsibleParty.NONE, rulingDetail);
	}
}
