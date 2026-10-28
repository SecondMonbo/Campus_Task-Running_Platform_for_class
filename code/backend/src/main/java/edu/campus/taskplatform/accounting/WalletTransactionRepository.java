package edu.campus.taskplatform.accounting;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

	List<WalletTransaction> findByTaskIdAndType(String taskId, WalletTransactionType type);

	Page<WalletTransaction> findByUserId(Long userId, Pageable pageable);

	Page<WalletTransaction> findByUserIdAndType(Long userId, WalletTransactionType type, Pageable pageable);
}
