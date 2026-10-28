package edu.campus.taskplatform.auth;

import edu.campus.taskplatform.common.ApiResponse;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final AuthSessionService authSessionService;
	private final CurrentUserService currentUserService;

	public AuthController(AuthService authService, AuthSessionService authSessionService,
			CurrentUserService currentUserService) {
		this.authService = authService;
		this.authSessionService = authSessionService;
		this.currentUserService = currentUserService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<AuthUserDto> register(@Valid @RequestBody RegisterRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AuthUserDto user = authService.register(request);
		authSessionService.signIn(user, httpRequest, httpResponse);
		return ApiResponse.ok(user);
	}

	@PostMapping("/login")
	public ApiResponse<AuthUserDto> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		AuthUserDto user = authService.authenticate(request);
		authSessionService.signIn(user, httpRequest, httpResponse);
		return ApiResponse.ok(user);
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		authSessionService.signOut(request, response);
		return ApiResponse.ok(null);
	}

	@GetMapping("/me")
	public ApiResponse<AuthUserDto> currentUser() {
		CurrentUser current = currentUserService.requireCurrentUser();
		return ApiResponse.ok(authService.getUser(current.userId()));
	}
}
