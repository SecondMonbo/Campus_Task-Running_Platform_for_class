package edu.campus.taskplatform.complaint;

import java.time.Instant;
import java.util.List;

public record ComplaintDto(
		String complaintId,
		String reporterId,
		String targetUserId,
		String taskId,
		String category,
		String description,
		ComplaintStatus status,
		List<ComplaintAction> actions,
		String decisionNote,
		String handledBy,
		Instant handledAt,
		Instant createdAt) {

	public static ComplaintDto from(Complaint complaint) {
		return new ComplaintDto(complaint.getComplaintId().toString(), complaint.getReporterId().toString(),
				complaint.getTargetUserId() == null ? null : complaint.getTargetUserId().toString(),
				complaint.getTaskId(), complaint.getCategory(), complaint.getDescription(), complaint.getStatus(),
				complaint.getActions(), complaint.getDecisionNote(),
				complaint.getHandledBy() == null ? null : complaint.getHandledBy().toString(),
				complaint.getHandledAt(), complaint.getCreatedAt());
	}
}
