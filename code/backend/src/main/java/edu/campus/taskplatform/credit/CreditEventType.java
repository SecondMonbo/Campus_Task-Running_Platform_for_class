package edu.campus.taskplatform.credit;

/**
 * 信用分事件类型。每条变更记录以 (eventType, sourceId) 唯一，保证同一业务事件只记分一次。
 * 本期实现评价、按时完成、超时送达与初始化；取消责任、投诉处罚、管理员调整与连续履约恢复
 * 已在文档中约定，但尚未接入。
 */
public enum CreditEventType {

    INITIALIZATION("初始化"),
    REVIEW_5_STAR("收到 5 星评价"),
    REVIEW_NEUTRAL("收到 3–4 星评价"),
    REVIEW_LOW_STAR("收到 1–2 星评价"),
    ON_TIME_COMPLETION("按时完成任务"),
    OVERTIME_DELIVERY("超时送达");

    private final String label;

    CreditEventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
