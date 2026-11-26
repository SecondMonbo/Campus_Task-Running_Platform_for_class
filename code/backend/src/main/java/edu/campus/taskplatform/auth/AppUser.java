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

	protected AppUser() {
	}

	public AppUser(String username, String passwordHash, String displayName, String phone, Instant createdAt) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.phone = phone;
		this.role = AppUserRole.USER;
		this.createdAt = createdAt;
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
