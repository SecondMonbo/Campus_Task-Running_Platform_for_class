package edu.campus.taskplatform.complaint;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {

	Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select complaint from Complaint complaint where complaint.complaintId = :id")
	Optional<Complaint> findByIdForUpdate(@Param("id") UUID id);
}
