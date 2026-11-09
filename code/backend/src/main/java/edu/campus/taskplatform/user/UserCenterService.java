package edu.campus.taskplatform.user;

import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.task.TaskReviewRepository;
import edu.campus.taskplatform.user.dto.ChangePasswordRequest;
import edu.campus.taskplatform.user.dto.UpdateProfileRequest;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户中心：个人资料编辑与密码修改。
 * 身份一律取自登录会话，请求体不接收 userId；密码校验与编码复用登录模块的
 * {@link PasswordEncoder}，与注册、登录保持同一套逻辑。
 */
@Service
public class UserCenterService {

	private final AppUserRepository userRepository;
	private final TaskReviewRepository reviewRepository;
	private final PasswordEncoder passwordEncoder;

	public UserCenterService(AppUserRepository userRepository, TaskReviewRepository reviewRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.reviewRepository = reviewRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public UserProfileDto profile(long userId) {
		return toProfile(requireUser(userId));
	}

	@Transactional
	public UserProfileDto updateProfile(long userId, UpdateProfileRequest request) {
		AppUser user = requireUser(userId);
		user.updateProfile(request.displayName().trim(), trimToNull(request.phone()));
		userRepository.saveAndFlush(user);
		return toProfile(user);
	}

	@Transactional
	public void changePassword(long userId, ChangePasswordRequest request) {
		AppUser user = requireUser(userId);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "当前密码不正确");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new TaskException(ErrorCode.VALIDATION_ERROR, "新密码不能与当前密码相同");
		}
		user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.saveAndFlush(user);
	}

	private AppUser requireUser(long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new TaskException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录"));
	}

	private UserProfileDto toProfile(AppUser user) {
		Double average = reviewRepository.averageScoreByRevieweeId(user.getUserId());
		return new UserProfileDto(user.getUserId().toString(), user.getUsername(), user.getDisplayName(),
				user.getPhone(), user.getRole().name(), user.getCreatedAt(),
				average == null ? null : Math.round(average * 10.0) / 10.0,
				reviewRepository.countByRevieweeId(user.getUserId()),
				// 信誉分展示接口预留：契约中的 creditScore（0-100）待信誉分机制上线后计算，
				// 当前返回 null，前端已按占位方式渲染。
				null);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
