package edu.campus.taskplatform.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import edu.campus.taskplatform.accounting.WalletTransaction;
import edu.campus.taskplatform.accounting.WalletTransactionRepository;
import edu.campus.taskplatform.accounting.WalletTransactionType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通过真实安全过滤器、会话适配器与数据库验证管理接口。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class AdminEndpointsIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired AppUserRepository users;
	@Autowired TaskRepository tasks;
	@Autowired ComplaintRepository complaints;
	@Autowired WalletTransactionRepository transactions;

	Long reporterId;
	Long targetId;
	Long adminId;
	String taskId;
	MockHttpSession userSession;
	MockHttpSession adminSession;
	final List<UUID> createdComplaints = new ArrayList<>();
	static final String ACTION_BODY = """
			{"actions":["LOG_ONLY","WARNING"],"decisionNote":" 已核对情况并记录 "}
			""";

	@BeforeEach
	void setUp() {
		String suffix = UUID.randomUUID().toString();
		reporterId = users.saveAndFlush(new AppUser("reporter-" + suffix, "unused-test-hash",
				"测试用户", null, Instant.now())).getUserId();
		targetId = users.saveAndFlush(new AppUser("target-" + suffix, "unused-test-hash",
				"任务参与者", null, Instant.now())).getUserId();
		adminId = users.saveAndFlush(new AppUser("admin-" + suffix, "unused-test-hash",
				"测试管理员", null, Instant.now())).getUserId();
		taskId = UUID.randomUUID().toString();
		Task task = new Task(taskId, reporterId, "测试任务", "测试说明", 20,
				Instant.now().plusSeconds(3600), "取件点", "送达点", Instant.now());
		task.setRunnerId(targetId);
		tasks.saveAndFlush(task);
		userSession = session(reporterId, "USER", "USER");
		adminSession = session(adminId, "ADMIN", "ADMIN");
		createdComplaints.clear();
	}

	@AfterEach
	void cleanCommittedConcurrencyFixtures() {
		if (!TestTransaction.isActive()) {
			createdComplaints.forEach(complaints::deleteById);
			tasks.deleteById(taskId);
			users.deleteAllById(List.of(reporterId, targetId, adminId));
		}
	}

	@Test
	void anonymousAndOrdinaryUsersCannotAccessAdminEndpoints() throws Exception {
		UUID id = pending().getComplaintId();
		for (String path : List.of("/api/v1/admin/complaints", "/api/v1/admin/transactions")) {
			mvc.perform(get(path)).andExpect(status().isUnauthorized());
			mvc.perform(get(path).session(userSession).param("role", "ADMIN"))
					.andExpect(status().isForbidden());
		}
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id).session(userSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
				.andExpect(status().isForbidden());
		assertEquals(ComplaintStatus.PENDING, complaints.findById(id).orElseThrow().getStatus());
	}

	@Test
	void servicesRecheckTheRoleStoredInTheBusinessSession() throws Exception {
		var mismatchedSession = session(reporterId, "USER", "ADMIN");
		for (String path : List.of("/api/v1/admin/complaints", "/api/v1/admin/transactions")) {
			mvc.perform(get(path).session(mismatchedSession)).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", pending().getComplaintId())
					.session(mismatchedSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
					.content(ACTION_BODY))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void writesRequireCsrfAndCreationRequiresAuthentication() throws Exception {
		UUID id = pending().getComplaintId();
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id).session(adminSession)
					.contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/complaints").with(csrf()).contentType(MediaType.APPLICATION_JSON)
					.content("{\"category\":\"其他\",\"description\":\"一般问题\"}"))
				.andExpect(status().isUnauthorized());
		assertEquals(ComplaintStatus.PENDING, complaints.findById(id).orElseThrow().getStatus());
	}

	@Test
	void createComplaintUsesSessionIdentityAndTaskCounterpart() throws Exception {
		mvc.perform(post("/api/v1/complaints").session(userSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content("""
							{"taskId":"%s","category":" 物品损坏 ","description":" 具体情况 ",
							 "userId":"%s","role":"ADMIN"}
							""".formatted(taskId, adminId)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("OK"))
				.andExpect(jsonPath("$.data.complaintId").isString())
				.andExpect(jsonPath("$.data.reporterId").value(reporterId.toString()))
				.andExpect(jsonPath("$.data.targetUserId").value(targetId.toString()))
				.andExpect(jsonPath("$.data.category").value("物品损坏"))
				.andExpect(jsonPath("$.data.description").value("具体情况"))
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.actions").isEmpty())
				.andExpect(jsonPath("$.data.createdAt").isString());
		mvc.perform(post("/api/v1/complaints").session(userSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"category\":\"其他\",\"description\":\"无关联任务的问题\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.data.taskId").doesNotExist());
	}

	@Test
	void creationRejectsUnrelatedTasksAndTargets() throws Exception {
		mvc.perform(post("/api/v1/complaints").session(adminSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(complaintBody(taskId, targetId)))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/complaints").session(userSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(complaintBody(taskId, adminId)))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/complaints").session(userSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(complaintBody(UUID.randomUUID().toString(), targetId)))
				.andExpect(status().isNotFound());
		for (Long target : List.of(reporterId, Long.MAX_VALUE)) {
			mvc.perform(post("/api/v1/complaints").session(userSession).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(complaintBody(null, target)))
					.andExpect(target.equals(reporterId) ? status().isBadRequest() : status().isNotFound());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{}", "{\"category\":\" \",\"description\":\"问题\"}",
			"{\"category\":\"其他\",\"description\":\"   \"}",
			"{\"category\":\"其他\",\"description\":\"问题\",\"targetUserId\":\"9223372036854775808\"}"
	})
	void creationValidatesInput(String body) throws Exception {
		mvc.perform(post("/api/v1/complaints").session(userSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void processingPersistsAuditFieldsAndRejectsRepeatedHandling() throws Exception {
		UUID id = pending().getComplaintId();
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id).session(adminSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROCESSED"))
				.andExpect(jsonPath("$.data.actions.length()").value(2))
				.andExpect(jsonPath("$.data.decisionNote").value("已核对情况并记录"))
				.andExpect(jsonPath("$.data.handledBy").value(adminId.toString()))
				.andExpect(jsonPath("$.data.handledAt").isString());
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id).session(adminSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"actions\":[\"LOG_ONLY\"],\"decisionNote\":\"覆盖原结论\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_ACTION"));
		mvc.perform(get("/api/v1/admin/complaints").session(adminSession).param("status", "PROCESSED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].decisionNote").value("已核对情况并记录"));
		mvc.perform(get("/api/v1/admin/complaints").session(adminSession).param("status", "PENDING"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{}", "{\"actions\":[],\"decisionNote\":\"说明\"}",
			"{\"actions\":[null],\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"UNKNOWN\"],\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"LOG_ONLY\"],\"decisionNote\":\"   \"}",
			"{\"actions\":[\"LOG_ONLY\",\"LOG_ONLY\"],\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"CREDIT_ADJUSTMENT\"],\"creditAdjustment\":-2,\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"FINANCIAL_ADJUSTMENT\"],\"financialAdjustment\":10,\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"ACCOUNT_FREEZE\"],\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"FUNCTION_RESTRICTION\"],\"decisionNote\":\"说明\"}",
			"{\"actions\":[\"LOG_ONLY\"],\"financialAdjustment\":0,\"decisionNote\":\"说明\"}"
	})
	void invalidOrUnsupportedHandlingDoesNotMarkComplaintProcessed(String body) throws Exception {
		UUID id = pending().getComplaintId();
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id).session(adminSession).with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		assertEquals(ComplaintStatus.PENDING, complaints.findById(id).orElseThrow().getStatus());
		assertTrue(complaints.findById(id).orElseThrow().getActions().isEmpty());
	}

	@Test
	void missingAndMalformedComplaintIdsHaveContractErrors() throws Exception {
		mvc.perform(post("/api/v1/admin/complaints/{id}/actions", UUID.randomUUID())
					.session(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
				.andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/admin/complaints/not-a-uuid/actions")
					.session(adminSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
				.andExpect(status().isBadRequest());
	}

	@Test
	void complaintsArePagedAndEmptyPagesSucceed() throws Exception {
		for (int i = 0; i < 13; i++) pending();
		var first = mvc.perform(get("/api/v1/admin/complaints").session(adminSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(10)).andExpect(jsonPath("$.data.total").value(13))
				.andExpect(jsonPath("$.data.items.length()").value(10)).andReturn();
		var second = mvc.perform(get("/api/v1/admin/complaints").session(adminSession).param("page", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(3)).andReturn();
		var ids = new HashSet<String>(JsonPath.<List<String>>read(first.getResponse().getContentAsString(),
				"$.data.items[*].complaintId"));
		ids.addAll(JsonPath.read(second.getResponse().getContentAsString(), "$.data.items[*].complaintId"));
		assertEquals(13, ids.size());
		mvc.perform(get("/api/v1/admin/complaints").session(adminSession).param("page", "99"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.total").value(13)).andExpect(jsonPath("$.data.page").value(99));
	}

	@Test
	void transactionsUseDatabaseFilteringAndStablePaginationAcrossAllUsers() throws Exception {
		seedTransactions();
		var ids = new HashSet<String>();
		for (int page = 0; page < 3; page++) {
			var response = mvc.perform(get("/api/v1/admin/transactions").session(adminSession)
						.param("page", Integer.toString(page)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.page").value(page))
					.andExpect(jsonPath("$.data.size").value(10)).andExpect(jsonPath("$.data.total").value(23))
					.andExpect(jsonPath("$.data.items.length()").value(page == 2 ? 3 : 10)).andReturn();
			List<String> pageIds = JsonPath.read(response.getResponse().getContentAsString(), "$.data.items[*].transactionId");
			assertEquals(new UUID(0, 23 - page * 10).toString(), pageIds.getFirst());
			ids.addAll(pageIds);
		}
		assertEquals(23, ids.size());
		mvc.perform(get("/api/v1/admin/transactions").session(adminSession).param("userId", reporterId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(19));
		mvc.perform(get("/api/v1/admin/transactions").session(adminSession).param("taskId", taskId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(17));
		mvc.perform(get("/api/v1/admin/transactions").session(adminSession).param("userId", reporterId.toString())
					.param("taskId", taskId).param("page", "1"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(13))
				.andExpect(jsonPath("$.data.items.length()").value(3))
				.andExpect(jsonPath("$.data.items[0].userId").value(reporterId.toString()))
				.andExpect(jsonPath("$.data.items[2].appealId").value(new UUID(0, 100).toString()));
		mvc.perform(get("/api/v1/admin/transactions").session(adminSession).param("taskId", "unknown"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.total").value(0));
		mvc.perform(get("/api/v1/admin/transactions").session(adminSession).param("page", "99"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
		// 管理员的全量查询不能改变普通钱包接口的本人范围。
		mvc.perform(get("/api/v1/wallet/transactions").session(userSession).param("userId", targetId.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(19));
	}

	@ParameterizedTest
	@ValueSource(ints = {10, 20, 50})
	void listEndpointsAcceptOnlyTheAgreedPageSizes(int size) throws Exception {
		for (String resource : List.of("complaints", "transactions")) {
			mvc.perform(get("/api/v1/admin/" + resource).session(adminSession).param("size", Integer.toString(size)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(size));
		}
	}

	@ParameterizedTest
	@CsvSource({"-1,10", "0,15", "0,0", "0,100", "invalid,10"})
	void listEndpointsRejectInvalidPagination(String page, String size) throws Exception {
		for (String resource : List.of("complaints", "transactions")) {
			mvc.perform(get("/api/v1/admin/" + resource).session(adminSession).param("page", page).param("size", size))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Test
	void queriesRejectInvalidStatusAndUserIdentifiers() throws Exception {
		mvc.perform(get("/api/v1/admin/complaints").session(adminSession).param("status", "UNKNOWN"))
				.andExpect(status().isBadRequest());
		for (String userId : List.of("-1", "0", "invalid", "9223372036854775808")) {
			mvc.perform(get("/api/v1/admin/transactions").session(adminSession).param("userId", userId))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentHandlingCommitsOnlyOneAuditRecord() throws Exception {
		UUID id = pending().getComplaintId();
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
			for (int i = 0; i < 2; i++) {
				var requestSession = session(adminId, "ADMIN", "ADMIN");
				futures.add(executor.submit(() -> {
					start.await();
					return mvc.perform(post("/api/v1/admin/complaints/{id}/actions", id)
							.session(requestSession).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(ACTION_BODY))
							.andReturn().getResponse().getStatus();
				}));
			}
			start.countDown();
			assertEquals(Set.of(200, 409), Set.of(futures.get(0).get(15, TimeUnit.SECONDS),
					futures.get(1).get(15, TimeUnit.SECONDS)));
		}
		mvc.perform(get("/api/v1/admin/complaints").session(adminSession).param("status", "PROCESSED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
				.andExpect(jsonPath("$.data.items[0].actions.length()").value(2));
	}

	private Complaint pending() {
		Complaint complaint = complaints.saveAndFlush(new Complaint(reporterId, targetId, taskId, "其他", "测试问题"));
		createdComplaints.add(complaint.getComplaintId());
		return complaint;
	}

	private void seedTransactions() {
		transactions.deleteAll();
		for (int i = 1; i <= 23; i++) {
			Long ownerId = i >= 14 && i <= 17 ? targetId : reporterId;
			String relatedTask = i <= 17 ? taskId : i <= 20 ? new UUID(0, 50).toString() : null;
			transactions.save(new WalletTransaction(new UUID(0, i), ownerId, relatedTask,
					i == 1 ? new UUID(0, 100) : null, WalletTransactionType.INCOME,
					i, 100 + i, Instant.parse("2026-11-01T00:00:00Z")));
		}
		transactions.flush();
	}

	private String complaintBody(String relatedTask, Long target) {
		return """
				{"taskId":%s,"targetUserId":"%s","category":"其他","description":"具体情况"}
				""".formatted(relatedTask == null ? "null" : "\"" + relatedTask + "\"", target);
	}

	private MockHttpSession session(Long userId, String sessionRole, String securityRole) {
		var session = new MockHttpSession();
		session.setAttribute(SessionCurrentUserService.SESSION_ATTRIBUTE, new CurrentUser(userId, sessionRole));
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("test-user-" + userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + securityRole))));
		session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
		return session;
	}
}
