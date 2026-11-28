package edu.campus.taskplatform.credit;

/**
 * 信用分事件类型。每条变更记录以 (eventType, sourceId) 唯一，保证同一业务事件只记分一次。
 *
 * <p>评价、完成、取消责任、投诉处罚、连续履约恢复与管理员复核调整均已接入；
 * {@link #INITIALIZATION} 只写基线记录，不改变分值。</p>
 */
public enum CreditEventType {

    /** 首次启用机制时的基线记录，变动值为 0。 */
    INITIALIZATION("初始化"),
    /** 收到 5 星评价，+2。 */
    REVIEW_5_STAR("收到 5 星评价"),
    /** 收到 3–4 星评价，0 分留痕。 */
    REVIEW_NEUTRAL("收到 3–4 星评价"),
    /** 收到 1–2 星评价；30 天内同一评价人首次扣 -3，后续为 0 分留痕。 */
    REVIEW_LOW_STAR("收到 1–2 星评价"),
    /** 按时完成任务，+1。 */
    ON_TIME_COMPLETION("按时完成任务"),
    /** 截止后 24 小时内完成，仅累计连续履约，不加减分。 */
    COMPLETION_IN_GRACE("截止后 24 小时内完成"),
    /** 超时超过 24 小时送达，-5。 */
    OVERTIME_DELIVERY("超时送达"),
    /** 取消争议中认定的有责方，-5。 */
    RESPONSIBLE_CANCELLATION("有责取消"),
    /** 投诉成立，按情节扣 -2 / -5 / -10。 */
    COMPLAINT_UPHELD("投诉成立"),
    /** 连续完成 10 个任务且无负向事件，补至 75 分。 */
    CONSECUTIVE_COMPLETION_RECOVERY("连续履约恢复"),
    /** 管理员复核调整，由管理员指定整数。 */
    ADMIN_REVIEW_ADJUSTMENT("管理员复核调整");

    private final String label;

    CreditEventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否属于“确认完成”类事件；这类事件参与连续履约计数。 */
    public boolean isCompletion() {
        return this == ON_TIME_COMPLETION
                || this == COMPLETION_IN_GRACE
                || this == OVERTIME_DELIVERY;
    }
}
