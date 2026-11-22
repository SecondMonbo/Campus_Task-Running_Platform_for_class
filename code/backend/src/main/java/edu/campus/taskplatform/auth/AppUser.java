package edu.campus.taskplatform.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "username", nullable = false, unique = true, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 50)
	private String displayName;

	@Column(name = "phone", length = 30)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 16)
	private AppUserRole role;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 信用分，取值 0–100；新用户初始化为 80。与钱包虚拟币相互独立。 */
	@Column(name = "credit_score", nullable = false)
	private int creditScore = 80;

	/** 低分限制状态：分数低于 60 时开启，恢复至 75 及以上才解除。 */
	@Column(name = "credit_restricted", nullable = false)
	private boolean creditRestricted;

	/** 连续完成且期间没有负向信用事件的任务数，用于恢复至 75 分。 */
	@Column(name = "consecutive_completed_tasks", nullable = false)
	private int consecutiveCompletedTasks;

	protected AppUser() {
	}

	public AppUser(String username, String passwordHash, String displayName, String phone, Instant createdAt) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.phone = phone;
		this.role = AppUserRole.USER;
		this.createdAt = createdAt;
		this.creditScore = 80;
		this.creditRestricted = false;
		this.consecutiveCompletedTasks = 0;
	}

	public Long getUserId() {
		return userId;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPhone() {
		return phone;
	}

	public AppUserRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public int getCreditScore() {
		return creditScore;
	}

	public boolean isCreditRestricted() {
		return creditRestricted;
	}

	public int getConsecutiveCompletedTasks() {
		return consecutiveCompletedTasks;
	}

	/** 信用分服务：整行更新分数、限制状态与连续完成计数。 */
	public void updateCredit(int creditScore, boolean creditRestricted, int consecutiveCompletedTasks) {
		this.creditScore = creditScore;
		this.creditRestricted = creditRestricted;
		this.consecutiveCompletedTasks = consecutiveCompletedTasks;
	}

	/** 用户中心：更新个人资料。显示名与手机号由调用方完成校验和清洗。 */
	public void updateProfile(String displayName, String phone) {
		this.displayName = displayName;
		this.phone = phone;
	}

	/** 用户中心：更新密码哈希，明文校验与编码由调用方负责。 */
	public void updatePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
