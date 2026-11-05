package edu.campus.taskplatform.accounting;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from WalletAccount account where account.userId = :userId")
	Optional<WalletAccount> findByIdForUpdate(@Param("userId") Long userId);
}
