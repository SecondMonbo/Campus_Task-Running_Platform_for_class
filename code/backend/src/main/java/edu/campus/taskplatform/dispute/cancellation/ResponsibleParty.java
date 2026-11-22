package edu.campus.taskplatform.dispute.cancellation;

/**
 * 取消争议的责任方。责任判定独立于退款/支付金额，必须由应答人确认或管理员明确选择。
 */
public enum ResponsibleParty {
	/** 取消申请提出方。 */
	REQUESTER,
	/** 被申请方。 */
	RESPONDER,
	/** 双方无责。 */
	NONE;

	public boolean isResponsible() {
		return this != NONE;
	}
}
