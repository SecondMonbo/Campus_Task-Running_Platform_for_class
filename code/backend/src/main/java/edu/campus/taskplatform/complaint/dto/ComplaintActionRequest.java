package edu.campus.taskplatform.complaint.dto;

import java.util.List;

import edu.campus.taskplatform.complaint.ComplaintAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintActionRequest(
		@NotEmpty @Size(max = 7) List<@NotNull ComplaintAction> actions,
		Integer creditAdjustment,
		Integer financialAdjustment,
		@NotBlank @Size(max = 1000) String decisionNote) {
}
