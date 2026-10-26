package edu.campus.taskplatform.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 撤回任务请求，字段与 openapi.yaml 的 WithdrawalRequest 一致。
 */
public class WithdrawalRequest {

    @NotBlank(message = "撤回原因不能为空")
    @Size(max = 200, message = "撤回原因长度不能超过200")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
