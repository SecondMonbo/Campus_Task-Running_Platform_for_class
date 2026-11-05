package edu.campus.taskplatform.auth;

import java.time.Instant;

import edu.campus.taskplatform.accounting.AccountingService;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.TaskException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	public static final long INITIAL_WALLET_BALANCE = 100;

	private final AppUserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountingService accountingService;

	public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
			AccountingService accountingService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.accountingService = accountingService;
	}

	@Transactional
	public AuthUserDto register(RegisterRequest request) {
		String username = request.username().trim();
		AppUser user = new AppUser(username, passwordEncoder.encode(request.password()),
				request.displayName().trim(), trimToNull(request.phone()), Instant.now());
		try {
			userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw new TaskException(ErrorCode.DUPLICATE_ACTION, "用户名已被使用");
		}
		accountingService.openAccount(user.getUserId(), INITIAL_WALLET_BALANCE);
		return AuthUserDto.from(user);
	}

	@Transactional(readOnly = true)
	public AuthUserDto authenticate(LoginRequest request) {
		AppUser user = userRepository.findByUsername(request.username().trim())
				.orElseThrow(AuthService::invalidCredentials);
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw invalidCredentials();
		}
		return AuthUserDto.from(user);
	}

	@Transactional(readOnly = true)
	public AuthUserDto getUser(long userId) {
		return userRepository.findById(userId)
				.map(AuthUserDto::from)
				.orElseThrow(() -> new TaskException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录"));
	}

	private static TaskException invalidCredentials() {
		return new TaskException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
