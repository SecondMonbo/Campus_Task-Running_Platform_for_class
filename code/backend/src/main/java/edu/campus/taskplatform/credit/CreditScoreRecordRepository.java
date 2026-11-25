package edu.campus.taskplatform.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditScoreRecordRepository extends JpaRepository<CreditScoreRecord, String> {

    boolean existsByEventTypeAndSourceId(CreditEventType eventType, String sourceId);

    Optional<CreditScoreRecord> findByEventTypeAndSourceId(CreditEventType eventType, String sourceId);

    Page<CreditScoreRecord> findByUserId(Long userId, Pageable pageable);
}
