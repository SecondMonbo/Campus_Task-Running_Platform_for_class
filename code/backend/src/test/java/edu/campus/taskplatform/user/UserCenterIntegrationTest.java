package edu.campus.taskplatform.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import edu.campus.taskplatform.auth.AppUser;
import edu.campus.taskplatform.auth.AppUserRepository;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.SessionCurrentUserService;
import edu.campus.taskplatform.complaint.Complaint;
import edu.campus.taskplatform.complaint.ComplaintRepository;
import edu.campus.taskplatform.complaint.ComplaintStatus;
import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskRepository;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 用户中心（资料编辑 / 密码修改）与用户侧非法任务举报的端到端校验。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class UserCenterIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired AppUserRepository users;
	@Autowired TaskRepository tasks;
	@Autowired ComplaintRepository complaints;
	@Autowired PasswordEncoder passwordEncoder;

	Long selfId;
	Long publisherId;
	Long runnerId;
	Long strangerId;
	String taskId;
	MockHttpSession selfSession;
	final List<UUID> createdComplaints = new ArrayList<>();

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString();
		selfId = users.saveAndFlush(new AppUser("self-" + suffix, passwordEncoder.encode("User123456"),
				"本人昵称", "13800000000", Instant.now())).getUserId();
		publisherId = users.saveAndFlush(new AppUser("publisher-" + suffix, "unused-test-hash",
				"发布者", null, Instant.now())).getUserId();
		runnerId = users.saveAndFlush(new AppUser("runner-" + suffix, "unused-test-hash",
				"跑腿员", null, Instant.now())).getUserId();
		strangerId = users.saveAndFlush(new AppUser("stranger-" + suffix, "unused-test-hash",
				"路人", null, Instant.now())).getUserId();
		taskId = UUID.randomUUID().toString();
		Task task = new Task(taskId, publisherId, "测试任务", "测试说明", 20,
				Instant.now().plusSeconds(3600), "取件点", "送达点", Instant.now());
		task.setRunnerId(runnerId);
		tasks.saveAndFlush(task);
		selfSession = session(selfId);
		createdComplaints.clear();
	}

	@AfterEach
	void cleanUp() {
		createdComplaints.forEach(complaints::deleteById);
		tasks.deleteById(taskId);
		users.deleteAllById(List.of(selfId, publisherId, runnerId, strangerId));
	}

	@Test
	void profileRequiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/users/me/profile")).andExpect(status().isUnauthorized());
		mvc.perform(patch("/api/v1/users/me").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(profileBody("新昵称", "13900000000"))).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/users/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(passwordBody("User123456", "User654321"))).andExpect(status().isUnauthorized());
	}

	@Test
	void profileReturnsOwnAccountAndRatingPlaceholder() throws Exception {
		mvc.perform(get("/api/v1/users/me/profile").session(selfSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("OK"))
				.andExpect(jsonPath("$.data.userId").value(selfId.toString()))
				.andExpect(jsonPath("$.data.username").value(users.findById(selfId).orElseThrow().getUsername()))
				.andExpect(jsonPath("$.data.displayName").value("本人昵称"))
				.andExpect(jsonPath("$.data.phone").value("13800000000"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andExpect(jsonPath("$.data.createdAt").isString())
				// 信誉分展示位：暂无评价时为空，评价数量为 0，creditScore 契约字段待接入。
				.andExpect(jsonPath("$.data.rating").doesNotExist())
				.andExpect(jsonPath("$.data.creditScore").doesNotExist())
				.andExpect(jsonPath("$.data.reviewCount").value(0));
	}

	@Test
	void updateProfilePersistsDisplayNameAndPhone() throws Exception {
		mvc.perform(patch("/api/v1/users/me").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(profileBody(" 新昵称 ", " 13900000000 ")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.displayName").value("新昵称"))
				.andExpect(jsonPath("$.data.phone").value("13900000000"));
		AppUser user = users.findById(selfId).orElseThrow();
		assertEquals("新昵称", user.getDisplayName());
		assertEquals("13900000000", user.getPhone());

		mvc.perform(patch("/api/v1/users/me").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(profileBody("新昵称", "  ")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").doesNotExist());
		assertNull(users.findById(selfId).orElseThrow().getPhone());
	}

	@Test
	void updateProfileRejectsBlankDisplayNameAndInvalidPhone() throws Exception {
		mvc.perform(patch("/api/v1/users/me").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(profileBody("   ", "13900000000")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		mvc.perform(patch("/api/v1/users/me").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(profileBody("昵称", "abc-")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertEquals("本人昵称", users.findById(selfId).orElseThrow().getDisplayName());
	}

	@Test
	void changePasswordValidatesCurrentPasswordAndPersistsHash() throws Exception {
		String originalHash = users.findById(selfId).orElseThrow().getPasswordHash();
		mvc.perform(post("/api/v1/users/me/password").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(passwordBody("Wrong123456", "User654321")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("当前密码不正确"));
		assertEquals(originalHash, users.findById(selfId).orElseThrow().getPasswordHash());

		mvc.perform(post("/api/v1/users/me/password").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(passwordBody("User123456", "User123456")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("新密码不能与当前密码相同"));

		mvc.perform(post("/api/v1/users/me/password").session(selfSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(passwordBody("User123456", "User654321")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"));
		String updatedHash = users.findById(selfId).orElseThrow().getPasswordHash();
		assertNotEquals(originalHash, updatedHash);
		assertTrue(passwordEncoder.matches("User654321", updatedHash));
	}

	@Test
	void nonParticipantCanReportTaskContentWithoutNamingTarget() throws Exception {
		// 路人（非任务参与者）可以直接举报非法任务内容，并保留 taskId 结构化关联。
		String body = """
				{"taskId":"%s","category":"违规内容","description":"任务描述含违规信息"}
				""".formatted(taskId);
		mvc.perform(post("/api/v1/complaints").session(session(strangerId)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.taskId").value(taskId))
				.andExpect(jsonPath("$.data.reporterId").value(strangerId.toString()))
				.andExpect(jsonPath("$.data.targetUserId").doesNotExist())
				.andExpect(jsonPath("$.data.status").value("PENDING"));
		Complaint saved = complaints.findAll().stream()
				.filter(item -> item.getReporterId().equals(strangerId)).findFirst().orElseThrow();
		createdComplaints.add(saved.getComplaintId());
		assertEquals(ComplaintStatus.PENDING, saved.getStatus());
		assertNull(saved.getTargetUserId());

		// 非参与者不能借举报之名指定被投诉用户。
		mvc.perform(post("/api/v1/complaints").session(session(strangerId)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"taskId":"%s","targetUserId":"%s","category":"其他","description":"测试"}
							""".formatted(taskId, publisherId)))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void participantComplaintStillAutoTargetsCounterpart() throws Exception {
		mvc.perform(post("/api/v1/complaints").session(session(publisherId)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"taskId\":\"%s\",\"category\":\"对方违约\",\"description\":\"未按时送达\"}"
							.formatted(taskId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.targetUserId").value(runnerId.toString()));
		Complaint saved = complaints.findAll().stream()
				.filter(item -> item.getReporterId().equals(publisherId)).findFirst().orElseThrow();
		createdComplaints.add(saved.getComplaintId());
		assertEquals(runnerId, saved.getTargetUserId());
	}

	private static String profileBody(String displayName, String phone) {
		return """
				{"displayName":"%s","phone":"%s"}
				""".formatted(displayName, phone);
	}

	private static String passwordBody(String currentPassword, String newPassword) {
		return """
				{"currentPassword":"%s","newPassword":"%s"}
				""".formatted(currentPassword, newPassword);
	}

	private MockHttpSession session(Long userId) {
		var httpSession = new MockHttpSession();
		httpSession.setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE, new CurrentUser(userId, "USER"));
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("test-user-" + userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		httpSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		return httpSession;
	}
}
