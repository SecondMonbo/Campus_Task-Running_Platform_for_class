package edu.campus.taskplatform.dispute.port;

import java.util.Optional;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;

/**
 * 当前登录用户端口。
 *
 * <p>实现者负责从 Spring Security 会话中解析用户 ID 与角色；取消/申诉模块不自行解析
 * 会话，也不接受请求体或查询参数传入的操作者身份。</p>
 */
public interface CurrentUserPort {

	Optional<AuthenticatedUser> currentUser();

	default AuthenticatedUser requireUser() {
		return currentUser().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
	}

	default AuthenticatedUser requireAdmin() {
		AuthenticatedUser user = requireUser();
		if (!user.isAdmin()) {
			throw new ApiException(ErrorCode.FORBIDDEN, "仅管理员可执行此操作");
		}
		return user;
	}
}
