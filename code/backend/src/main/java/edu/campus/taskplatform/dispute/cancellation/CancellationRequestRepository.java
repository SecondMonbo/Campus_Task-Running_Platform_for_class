package edu.campus.taskplatform.dispute.cancellation;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CancellationRequestRepository extends JpaRepository<CancellationRequestEntity, UUID> {

	/**
	 * 事务内加行锁读取申请，保证应答、裁决与并发请求串行化。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select request from CancellationRequestEntity request where request.requestId = :requestId")
	Optional<CancellationRequestEntity> findByIdForUpdate(@Param("requestId") UUID requestId);

	boolean existsByTaskIdAndStatusIn(String taskId, Collection<CancellationStatus> statuses);

	Page<CancellationRequestEntity> findByStatus(CancellationStatus status, Pageable pageable);
}
