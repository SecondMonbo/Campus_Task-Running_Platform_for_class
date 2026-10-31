package edu.campus.taskplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookiePath("/");
		return repository;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			SecurityContextRepository securityContextRepository,
			CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
		return http
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health", "/api/v1/auth/csrf",
								"/api/v1/auth/register", "/api/v1/auth/login").permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.build();
	}
}
