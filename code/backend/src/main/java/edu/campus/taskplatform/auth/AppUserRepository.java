package edu.campus.taskplatform.auth;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

	Optional<AppUser> findByUsername(String username);

	/** 信用分变更：锁定用户行，保证同一用户的分数串行更新。 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from AppUser u where u.userId = :userId")
	Optional<AppUser> findByIdForUpdate(@Param("userId") Long userId);

	List<AppUser> findByRole(AppUserRole role);
}
