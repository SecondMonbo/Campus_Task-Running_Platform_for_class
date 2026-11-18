package edu.campus.taskplatform.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditScoreRecordRepository extends JpaRepository<CreditScoreRecord, String> {

    boolean existsByEventTypeAndSourceId(CreditEventType eventType, String sourceId);

    Page<CreditScoreRecord> findByUserId(Long userId, Pageable pageable);
}
