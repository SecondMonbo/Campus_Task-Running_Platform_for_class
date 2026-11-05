package edu.campus.taskplatform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Size(min = 3, max = 50) String username,
		@NotBlank @Size(min = 8, max = 128) String password,
		@NotBlank @Size(min = 1, max = 50) String displayName,
		@Size(max = 30) String phone) {
}
