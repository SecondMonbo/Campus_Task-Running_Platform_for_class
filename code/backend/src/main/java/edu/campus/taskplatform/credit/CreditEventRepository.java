package edu.campus.taskplatform.credit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditEventRepository extends JpaRepository<CreditEvent, UUID> {

	List<CreditEvent> findByStatusInOrderByCreatedAtAsc(Collection<CreditEventStatus> statuses,
			Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from CreditEvent e where e.eventId = :eventId")
	Optional<CreditEvent> findByIdForUpdate(@Param("eventId") UUID eventId);
}
