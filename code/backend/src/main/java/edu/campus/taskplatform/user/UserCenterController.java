package edu.campus.taskplatform.user;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ApiResponse;
import edu.campus.taskplatform.user.dto.ChangePasswordRequest;
import edu.campus.taskplatform.user.dto.UpdateProfileRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户中心接口：资料查看、资料编辑、密码修改。 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserCenterController {

	private final UserCenterService service;
	private final CurrentUserService currentUserService;

	public UserCenterController(UserCenterService service, CurrentUserService currentUserService) {
		this.service = service;
		this.currentUserService = currentUserService;
	}

	@GetMapping("/profile")
	public ApiResponse<UserProfileDto> profile() {
		CurrentUser current = currentUserService.requireCurrentUser();
		return ApiResponse.ok(service.profile(current.userId()));
	}

	@PatchMapping
	public ApiResponse<UserProfileDto> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		CurrentUser current = currentUserService.requireCurrentUser();
		return ApiResponse.ok(service.updateProfile(current.userId(), request));
	}

	@PostMapping("/password")
	public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
		CurrentUser current = currentUserService.requireCurrentUser();
		service.changePassword(current.userId(), request);
		return ApiResponse.ok(null);
	}
}
