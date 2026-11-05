package edu.campus.taskplatform.dispute.port;

/**
 * 从登录会话解析出的当前用户；不接受前端传入的操作者身份。
 */
public record AuthenticatedUser(Long userId, UserRole role) {

	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}
}
