package edu.campus.taskplatform.dispute.config;

import java.util.Optional;

import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import edu.campus.taskplatform.common.ErrorCode;
import edu.campus.taskplatform.common.TaskException;
import edu.campus.taskplatform.dispute.port.AuthenticatedUser;
import edu.campus.taskplatform.dispute.port.CurrentUserPort;
import edu.campus.taskplatform.dispute.port.UserRole;

import org.springframework.stereotype.Component;

@Component
public class SessionCurrentUserAdapter implements CurrentUserPort {

	private final CurrentUserService currentUserService;

	public SessionCurrentUserAdapter(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	@Override
	public Optional<AuthenticatedUser> currentUser() {
		try {
			CurrentUser current = currentUserService.requireCurrentUser();
			UserRole role = switch (current.role()) {
				case "USER" -> UserRole.USER;
				case "ADMIN" -> UserRole.ADMIN;
				default -> throw new IllegalStateException("Unsupported session role: " + current.role());
			};
			return Optional.of(new AuthenticatedUser(current.userId(), role));
		}
		catch (TaskException exception) {
			if (exception.getErrorCode() == ErrorCode.UNAUTHORIZED) {
				return Optional.empty();
			}
			throw exception;
		}
	}
}
