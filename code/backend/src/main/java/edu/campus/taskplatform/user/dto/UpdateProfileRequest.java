package edu.campus.taskplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 个人资料编辑请求；账号名不可修改，仅允许调整显示名与手机号。 */
public record UpdateProfileRequest(
		@NotBlank @Size(min = 1, max = 50) String displayName,
		@Size(max = 30) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "手机号只能包含数字、空格、+ 或 -") String phone) {
}
