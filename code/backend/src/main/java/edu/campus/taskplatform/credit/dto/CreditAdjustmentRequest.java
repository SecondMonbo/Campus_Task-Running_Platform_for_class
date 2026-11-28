package edu.campus.taskplatform.credit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员复核调整信用分请求。调整值由管理员指定整数，结果为 0–100 截断；
 * 必须填写复核说明，历史记录只追加不删除。
 */
public record CreditAdjustmentRequest(
		@NotNull(message = "delta 不能为空")
		@Min(value = -100, message = "delta 不能小于 -100")
		@Max(value = 100, message = "delta 不能大于 100")
		Integer delta,

		@NotBlank(message = "复核说明不能为空")
		@Size(max = 500, message = "复核说明不能超过 500 个字符")
		String reason) {
}
