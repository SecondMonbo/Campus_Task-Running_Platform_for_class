package edu.campus.taskplatform.auth;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionService {

	private final SecurityContextRepository securityContextRepository;
	private final CsrfTokenRepository csrfTokenRepository;
	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	public AuthSessionService(SecurityContextRepository securityContextRepository,
			CsrfTokenRepository csrfTokenRepository) {
		this.securityContextRepository = securityContextRepository;
		this.csrfTokenRepository = csrfTokenRepository;
	}

	public void signIn(AuthUserDto user, HttpServletRequest request, HttpServletResponse response) {
		request.getSession(true);
		request.changeSessionId();

		String authority = "ROLE_" + user.role();
		var authentication = UsernamePasswordAuthenticationToken.authenticated(
				user.username(), null, List.of(new SimpleGrantedAuthority(authority)));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		request.getSession(false).setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE,
				new CurrentUser(Long.valueOf(user.userId()), user.role()));
		csrfTokenRepository.saveToken(null, request, response);
	}

	public void signOut(HttpServletRequest request, HttpServletResponse response) {
		logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
		csrfTokenRepository.saveToken(null, request, response);
	}
}
