package edu.campus.taskplatform.dispute.support;

import java.util.Optional;

import edu.campus.taskplatform.dispute.port.AuthenticatedUser;
import edu.campus.taskplatform.dispute.port.CurrentUserPort;
import edu.campus.taskplatform.dispute.port.UserRole;

/**
 * 测试用当前用户端口：由用例显式切换登录身份。
 */
public class MutableCurrentUserPort implements CurrentUserPort {

	private AuthenticatedUser current;

	@Override
	public Optional<AuthenticatedUser> currentUser() {
		return Optional.ofNullable(current);
	}

	public void signIn(Long userId, UserRole role) {
		this.current = new AuthenticatedUser(userId, role);
	}

	public void signInAsUser(Long userId) {
		signIn(userId, UserRole.USER);
	}

	public void signInAsAdmin(Long userId) {
		signIn(userId, UserRole.ADMIN);
	}

	public void signOut() {
		this.current = null;
	}
}
