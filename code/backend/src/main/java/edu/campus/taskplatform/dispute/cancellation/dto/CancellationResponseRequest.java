package edu.campus.taskplatform.dispute.cancellation.dto;

import edu.campus.taskplatform.dispute.cancellation.CancellationDecision;
import edu.campus.taskplatform.dispute.cancellation.ResponsibleParty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 取消申请应答请求体，对应 OpenAPI {@code CancellationResponseRequest}。
 * 拒绝时必须声明双方无责；同意时可确认申请方或被申请方为责任方。
 */
public record CancellationResponseRequest(
		@NotNull(message = "decision 不能为空")
		CancellationDecision decision,

		@NotNull(message = "responsibleParty 不能为空")
		ResponsibleParty responsibleParty,

		@Size(max = 500, message = "说明不能超过 500 个字符")
		String note) {

	/** 兼容旧调用：未明确责任方时按 NONE 处理，接口层仍要求显式传入。 */
	public CancellationResponseRequest(CancellationDecision decision, String note) {
		this(decision, ResponsibleParty.NONE, note);
	}
}
