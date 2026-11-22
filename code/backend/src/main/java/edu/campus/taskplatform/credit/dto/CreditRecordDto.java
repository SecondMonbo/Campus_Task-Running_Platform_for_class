package edu.campus.taskplatform.credit.dto;

import java.time.Instant;

import edu.campus.taskplatform.credit.CreditEventType;
import edu.campus.taskplatform.credit.CreditRecord;

/** 信用分变更记录视图；所有 ID 按字符串输出。 */
public record CreditRecordDto(
		String recordId,
		String userId,
		CreditEventType eventType,
		String sourceId,
		String reason,
		int requestedDelta,
		int actualDelta,
		Integer scoreBefore,
		int scoreAfter,
		String adminId,
		Instant createdAt) {

	public static CreditRecordDto from(CreditRecord record) {
		return new CreditRecordDto(record.getRecordId().toString(), record.getUserId().toString(),
				record.getEventType(), record.getSourceId(), record.getReason(),
				record.getRequestedDelta(), record.getActualDelta(), record.getScoreBefore(),
				record.getScoreAfter(),
				record.getAdminId() == null ? null : record.getAdminId().toString(),
				record.getCreatedAt());
	}
}
