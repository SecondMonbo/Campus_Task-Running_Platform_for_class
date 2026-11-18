package edu.campus.taskplatform.credit.dto;

import edu.campus.taskplatform.credit.CreditScoreRecord;

import java.time.Instant;

/** 单条信用分变更记录（本人与管理员可见；他人主页不返回）。 */
public record CreditRecordDto(
        String recordId,
        String eventType,
        String eventLabel,
        String reason,
        int requestedDelta,
        int actualDelta,
        Integer scoreBefore,
        int scoreAfter,
        Instant createdAt) {

    public static CreditRecordDto from(CreditScoreRecord record) {
        return new CreditRecordDto(record.getRecordId(), record.getEventType().name(),
                record.getEventType().label(), record.getReason(), record.getRequestedDelta(),
                record.getActualDelta(), record.getScoreBefore(), record.getScoreAfter(),
                record.getCreatedAt());
    }
}
