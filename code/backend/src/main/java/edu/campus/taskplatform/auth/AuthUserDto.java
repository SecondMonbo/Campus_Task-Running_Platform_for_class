package edu.campus.taskplatform.auth;

public record AuthUserDto(String userId, String username, String displayName, String role, String phone) {

	public static AuthUserDto from(AppUser user) {
		return new AuthUserDto(user.getUserId().toString(), user.getUsername(), user.getDisplayName(),
				user.getRole().name(), user.getPhone());
	}
}
