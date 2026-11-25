package edu.campus.taskplatform.credit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CreditScoreRepository extends JpaRepository<CreditScore, Long> {

    /**
     * 按用户加悲观写锁（SELECT ... FOR UPDATE），串行化同一用户的分值变更：
     * 并发事件在提交前不会读到相同旧分并互相覆盖。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CreditScore c where c.userId = :userId")
    Optional<CreditScore> findByIdForUpdate(@Param("userId") Long userId);
}
