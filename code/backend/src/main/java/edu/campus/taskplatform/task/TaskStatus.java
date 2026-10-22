package edu.campus.taskplatform.task;

/**
 * 任务状态，枚举值与 openapi.yaml 的 TaskStatus 完全一致。
 * 只能由合法业务操作按状态迁移表改变，不提供任意改状态的接口。
 */
public enum TaskStatus {
    /** 待接单 */
    WAITING_FOR_TAKER,
    /** 进行中 */
    IN_PROGRESS,
    /** 待验收 */
    WAITING_FOR_ACCEPTANCE,
    /** 已完成（终态） */
    COMPLETED,
    /** 已取消（终态） */
    CANCELLED
}
