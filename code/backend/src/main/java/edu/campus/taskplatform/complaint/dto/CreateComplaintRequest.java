package edu.campus.taskplatform.complaint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateComplaintRequest(
		@Size(max = 36) String taskId,
		@Pattern(regexp = "[1-9][0-9]{0,18}", message = "targetUserId 必须是正整数编号") String targetUserId,
		@NotBlank @Size(max = 100) String category,
		@NotBlank @Size(max = 2000) String description) {
}
