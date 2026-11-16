package edu.campus.taskplatform.notification;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.SessionCurrentUserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 管理员用户查询、系统通知发布以及通知类型筛选的集成测试。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class NotificationAdminIntegrationTest {

	private static final String NOTICE_BODY = """
			{"title":"  系统维护通知  ","content":"  今晚 22:00 起维护。  "}
			""";

	@Autowired MockMvc mvc;
	@Autowired AppUserRepository users;
	@Autowired NotificationRepository notifications;

	String suffix;
	Long adminId;
	Long aliceId;
	Long bobId;
	MockHttpSession adminSession;
	MockHttpSession aliceSession;

	@BeforeEach
	void setUp() {
		suffix = UUID.randomUUID().toString().substring(0, 8);
		adminId = save("admin-" + suffix, "测试管理员" + suffix, "1380000" + suffix);
		aliceId = save("alice-" + suffix, "测试用户甲" + suffix, "1390000" + suffix);
		bobId = save("bob-" + suffix, "测试用户乙" + suffix, "1370000" + suffix);
		adminSession = session(adminId, "ADMIN");
		aliceSession = session(aliceId, "USER");
	}

	@Test
	void adminEndpointsRejectAnonymousUsersOrdinaryUsersAndMissingCsrf() throws Exception {
		mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/admin/users").session(aliceSession)).andExpect(status().isForbidden());

		mvc.perform(post("/api/v1/admin/notifications").with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(NOTICE_BODY))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/admin/notifications").session(aliceSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(NOTICE_BODY))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/admin/notifications").session(adminSession)
					.contentType(MediaType.APPLICATION_JSON).content(NOTICE_BODY))
				.andExpect(status().isForbidden());
		assertEquals(0, notifications.count());
	}

	@Test
	void servicesRecheckTheRoleStoredInTheBusinessSession() throws Exception {
		// 安全上下文声称 ADMIN，但业务会话仍是普通用户：URL 层放行后必须由服务层拒绝。
		var mismatchedSession = session(aliceId, "USER", "ADMIN");
		mvc.perform(get("/api/v1/admin/users").session(mismatchedSession))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mvc.perform(post("/api/v1/admin/notifications").session(mismatchedSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(NOTICE_BODY))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		assertEquals(0, notifications.count());
	}

	@Test
	void adminQueriesUsersByUsernameDisplayNameOrPhone() throws Exception {
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("keyword", "alice-" + suffix))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"))
				.andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(10))
				.andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].userId").value(aliceId.toString()))
				.andExpect(jsonPath("$.data.items[0].username").value("alice-" + suffix))
				.andExpect(jsonPath("$.data.items[0].displayName").value("测试用户甲" + suffix))
				.andExpect(jsonPath("$.data.items[0].role").value("USER"))
				.andExpect(jsonPath("$.data.items[0].phone").value("1390000" + suffix))
				.andExpect(jsonPath("$.data.items[0].createdAt").isString())
				.andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("keyword", "用户乙" + suffix))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].userId").value(bobId.toString()));
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("keyword", "1370000" + suffix))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].userId").value(bobId.toString()));
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("keyword", "no-such-user-" + suffix))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.total").value(0));
	}

	@Test
	void adminUserQueryValidatesPaginationAndKeyword() throws Exception {
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("keyword", "x".repeat(101)))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		for (String size : List.of("0", "15", "100")) {
			mvc.perform(get("/api/v1/admin/users").session(adminSession).param("size", size))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("page", "-1"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(get("/api/v1/admin/users").session(adminSession).param("page", "99"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.page").value(99));
	}

	@Test
	void publishedSystemNoticeReachesEveryUserWithSystemType() throws Exception {
		long recipientCount = users.count();
		mvc.perform(post("/api/v1/admin/notifications").session(adminSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(NOTICE_BODY))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("OK"))
				.andExpect(jsonPath("$.data.recipientCount").value((int) recipientCount))
				.andExpect(jsonPath("$.data.publishedAt").isString());

		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "SYSTEM"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("SYSTEM"))
				.andExpect(jsonPath("$.data.items[0].title").value("系统维护通知"))
				.andExpect(jsonPath("$.data.items[0].content").value("今晚 22:00 起维护。"))
				.andExpect(jsonPath("$.data.items[0].read").value(false))
				.andExpect(jsonPath("$.data.items[0].relatedTaskId").value(nullValue()));
		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "TASK"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
		mvc.perform(get("/api/v1/notifications").session(session(bobId, "USER")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("SYSTEM"));
	}

	@Test
	void systemNoticesAreReadableAndFilteredLikeTaskNotices() throws Exception {
		mvc.perform(post("/api/v1/admin/notifications").session(adminSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(NOTICE_BODY))
				.andExpect(status().isCreated());
		String noticeId = readSystemNoticeIds(aliceSession).getFirst();
		mvc.perform(post("/api/v1/notifications/{id}/read", noticeId).session(aliceSession).with(csrf()))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "SYSTEM")
					.param("unreadOnly", "true"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "SYSTEM"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].read").value(true));
		// 其他用户的通知不能被读取。
		mvc.perform(post("/api/v1/notifications/{id}/read", noticeId)
					.session(session(bobId, "USER")).with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	void taskNotificationsKeepTaskTypeAndRelatedTaskId() throws Exception {
		String taskId = UUID.randomUUID().toString();
		notifications.saveAndFlush(new Notification(UUID.randomUUID().toString(), aliceId,
				NotificationType.TASK, "任务已完成", "报酬已结算。", taskId, Instant.now()));
		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "TASK"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].type").value("TASK"))
				.andExpect(jsonPath("$.data.items[0].relatedTaskId").value(taskId));
		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "SYSTEM"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
		mvc.perform(get("/api/v1/notifications").session(aliceSession).param("type", "UNKNOWN"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void systemNoticeValidatesTitleAndContent() throws Exception {
		for (String body : List.of(
				"{}",
				"{\"title\":\"   \",\"content\":\"正文\"}",
				"{\"title\":\"标题\",\"content\":\"   \"}",
				"{\"title\":\"" + "x".repeat(101) + "\",\"content\":\"正文\"}",
				"{\"title\":\"标题\",\"content\":\"" + "x".repeat(501) + "\"}")) {
			mvc.perform(post("/api/v1/admin/notifications").session(adminSession).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
		assertEquals(0, notifications.count());
	}

	private List<String> readSystemNoticeIds(MockHttpSession session) throws Exception {
		var response = mvc.perform(get("/api/v1/notifications").session(session).param("type", "SYSTEM"))
				.andExpect(status().isOk()).andReturn();
		return com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(),
				"$.data.items[*].notificationId");
	}

	private Long save(String username, String displayName, String phone) {
		return users.saveAndFlush(new AppUser(username, "unused-test-hash", displayName, phone,
				Instant.now())).getUserId();
	}

	private MockHttpSession session(Long userId, String role) {
		return session(userId, role, role);
	}

	private MockHttpSession session(Long userId, String sessionRole, String securityRole) {
		var session = new MockHttpSession();
		session.setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE,
				new CurrentUser(userId, sessionRole));
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("test-user-" + userId,
				null, List.of(new SimpleGrantedAuthority("ROLE_" + securityRole))));
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		return session;
	}
}
