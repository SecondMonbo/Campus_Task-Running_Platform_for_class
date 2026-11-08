package edu.campus.taskplatform.accounting;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID>,
		JpaSpecificationExecutor<WalletTransaction> {

	List<WalletTransaction> findByTaskIdAndType(String taskId, WalletTransactionType type);

	Page<WalletTransaction> findByUserId(Long userId, Pageable pageable);

	Page<WalletTransaction> findByUserIdAndType(Long userId, WalletTransactionType type, Pageable pageable);
}
