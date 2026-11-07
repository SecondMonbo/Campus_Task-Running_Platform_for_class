package edu.campus.taskplatform.complaint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "complaints")
public class Complaint {

	@Id
	@Column(name = "complaint_id", nullable = false, updatable = false)
	private UUID complaintId;

	@Column(name = "reporter_id", nullable = false, updatable = false)
	private Long reporterId;

	@Column(name = "target_user_id", updatable = false)
	private Long targetUserId;

	@Column(name = "task_id", length = 36, updatable = false)
	private String taskId;

	@Column(nullable = false, length = 100, updatable = false)
	private String category;

	@Column(nullable = false, length = 2000, updatable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ComplaintStatus status;

	@ElementCollection
	@CollectionTable(name = "complaint_actions", joinColumns = @JoinColumn(name = "complaint_id"))
	@OrderColumn(name = "position")
	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, length = 32)
	private List<ComplaintAction> actions = new ArrayList<>();

	@Column(name = "decision_note", length = 1000)
	private String decisionNote;

	@Column(name = "handled_by")
	private Long handledBy;

	@Column(name = "handled_at")
	private Instant handledAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Complaint() {
	}

	public Complaint(Long reporterId, Long targetUserId, String taskId, String category, String description) {
		this.complaintId = UUID.randomUUID();
		this.reporterId = reporterId;
		this.targetUserId = targetUserId;
		this.taskId = taskId;
		this.category = category;
		this.description = description;
		this.status = ComplaintStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public void process(Long adminId, List<ComplaintAction> actions, String decisionNote) {
		if (status != ComplaintStatus.PENDING) throw new IllegalStateException("投诉已处理");
		this.actions.addAll(actions);
		this.decisionNote = decisionNote;
		this.handledBy = adminId;
		this.handledAt = Instant.now();
		this.status = ComplaintStatus.PROCESSED;
	}

	public UUID getComplaintId() { return complaintId; }
	public Long getReporterId() { return reporterId; }
	public Long getTargetUserId() { return targetUserId; }
	public String getTaskId() { return taskId; }
	public String getCategory() { return category; }
	public String getDescription() { return description; }
	public ComplaintStatus getStatus() { return status; }
	public List<ComplaintAction> getActions() { return List.copyOf(actions); }
	public String getDecisionNote() { return decisionNote; }
	public Long getHandledBy() { return handledBy; }
	public Instant getHandledAt() { return handledAt; }
	public Instant getCreatedAt() { return createdAt; }
}
