package edu.campus.taskplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改密码请求；新密码强度与注册保持一致（8-128 位）。 */
public record ChangePasswordRequest(
		@NotBlank @Size(max = 128) String currentPassword,
		@NotBlank @Size(min = 8, max = 128) String newPassword) {
}
