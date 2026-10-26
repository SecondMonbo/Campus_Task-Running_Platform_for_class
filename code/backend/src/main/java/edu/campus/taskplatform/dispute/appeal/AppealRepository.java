package edu.campus.taskplatform.dispute.appeal;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppealRepository extends JpaRepository<AppealEntity, UUID> {

	/**
	 * 事务内加行锁读取申诉，保证同一申诉不会被并发重复裁决。
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select appeal from AppealEntity appeal where appeal.appealId = :appealId")
	Optional<AppealEntity> findByIdForUpdate(@Param("appealId") UUID appealId);

	boolean existsByTaskIdAndAppellantId(UUID taskId, UUID appellantId);

	Page<AppealEntity> findByStatus(AppealStatus status, Pageable pageable);
}
