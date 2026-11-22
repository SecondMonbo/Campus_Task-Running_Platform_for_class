package edu.campus.taskplatform.credit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditRecordRepository extends JpaRepository<CreditRecord, UUID> {

	Page<CreditRecord> findByUserId(Long userId, Pageable pageable);

	boolean existsByUserIdAndEventType(Long userId, CreditEventType eventType);
}
