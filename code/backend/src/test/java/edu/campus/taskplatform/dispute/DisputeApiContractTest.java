package edu.campus.taskplatform.dispute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import edu.campus.taskplatform.common.GlobalExceptionHandler;
import edu.campus.taskplatform.dispute.appeal.AdminAppealController;
import edu.campus.taskplatform.dispute.appeal.AppealController;
import edu.campus.taskplatform.dispute.appeal.AppealRepository;
import edu.campus.taskplatform.dispute.cancellation.AdminCancellationController;
import edu.campus.taskplatform.dispute.cancellation.CancellationController;
import edu.campus.taskplatform.dispute.cancellation.CancellationRequestRepository;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;
import edu.campus.taskplatform.dispute.port.TaskStatus;
import edu.campus.taskplatform.dispute.support.DisputeTestPortConfiguration;
import edu.campus.taskplatform.dispute.support.InMemoryLedgerPort;
import edu.campus.taskplatform.dispute.support.InMemoryTaskPort;
import edu.campus.taskplatform.dispute.support.MutableCurrentUserPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * 通过独立 MockMvc 校验控制器路径、HTTP 状态码与统一响应结构，不加载安全过滤器链
 * （登录与 CSRF 由组长统一实现，本测试聚焦本模块的契约输出）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DisputeTestPortConfiguration.class)
class DisputeApiContractTest {

	@Autowired
	CancellationController cancellationController;

	@Autowired
	AdminCancellationController adminCancellationController;

	@Autowired
	AppealController appealController;

	@Autowired
	AdminAppealController adminAppealController;

	@Autowired
	CancellationRequestRepository cancellationRepository;

	@Autowired
	AppealRepository appealRepository;

	@Autowired
	MutableCurrentUserPort currentUser;

	@Autowired
	InMemoryTaskPort taskPort;

	@Autowired
	InMemoryLedgerPort ledger;

	MockMvc mockMvc;

	Long publisher;
	Long runner;
	Long admin;
	String taskId;

	@BeforeEach
	void setUp() {
		appealRepository.deleteAll();
		cancellationRepository.deleteAll();
		taskPort.reset();
		ledger.reset();
		currentUser.signOut();

		publisher = 101L;
		runner = 102L;
		admin = 103L;
		taskId = UUID.randomUUID().toString();
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, TaskStatus.IN_PROGRESS, 30));

		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders
				.standaloneSetup(cancellationController, adminCancellationController,
						appealController, adminAppealController)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@Test
	void createCancellationRequest_returns201WithContractFields() throws Exception {
		currentUser.signInAsUser(publisher);

		mockMvc.perform(post("/api/v1/tasks/{taskId}/cancellation-requests", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"时间冲突\"}")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.code").value("OK"))
				.andExpect(jsonPath("$.message").value("success"))
				.andExpect(jsonPath("$.data.requestId").isString())
				.andExpect(jsonPath("$.data.taskId").value(taskId))
				.andExpect(jsonPath("$.data.requesterId").value(publisher.toString()))
				.andExpect(jsonPath("$.data.status").value("PENDING_RESPONSE"))
				.andExpect(jsonPath("$.data.refundAmount").value(0))
				.andExpect(jsonPath("$.data.runnerAmount").value(0));
	}

	@Test
	void createCancellationRequest_validatesBody() throws Exception {
		currentUser.signInAsUser(publisher);

		mockMvc.perform(post("/api/v1/tasks/{taskId}/cancellation-requests", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"  \"}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(post("/api/v1/tasks/{taskId}/cancellation-requests", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void createCancellationRequest_mapsUnknownTaskTo404() throws Exception {
		currentUser.signInAsUser(publisher);

		mockMvc.perform(post("/api/v1/tasks/{taskId}/cancellation-requests", UUID.randomUUID())
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"不存在\"}")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"))
				.andExpect(jsonPath("$.data").doesNotExist());

		mockMvc.perform(post("/api/v1/tasks/{taskId}/cancellation-requests", "not-a-uuid")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"格式错误\"}")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
	}

	@Test
	void respondToCancellationRequest_approveReturns200AndCancelsTask() throws Exception {
		currentUser.signInAsUser(publisher);
		String requestId = createCancellationRequest();

		currentUser.signInAsUser(runner);
		mockMvc.perform(post("/api/v1/cancellation-requests/{requestId}/response", requestId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"decision\":\"APPROVE\",\"note\":\"同意\"}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("APPROVED"))
				.andExpect(jsonPath("$.data.refundAmount").value(30))
				.andExpect(jsonPath("$.data.ruling").doesNotExist());
	}

	@Test
	void respondToCancellationRequest_rejectsMissingDecision() throws Exception {
		currentUser.signInAsUser(publisher);
		String requestId = createCancellationRequest();

		currentUser.signInAsUser(runner);
		mockMvc.perform(post("/api/v1/cancellation-requests/{requestId}/response", requestId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void adminCancellationList_returnsPagedDataForAdminOnly() throws Exception {
		currentUser.signInAsUser(publisher);
		createCancellationRequest();

		currentUser.signInAsUser(publisher);
		mockMvc.perform(get("/api/v1/admin/cancellation-requests"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		currentUser.signInAsAdmin(admin);
		mockMvc.perform(get("/api/v1/admin/cancellation-requests").param("status", "PENDING_RESPONSE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items[0].status").value("PENDING_RESPONSE"))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(10))
				.andExpect(jsonPath("$.data.total").value(1));
	}

	@Test
	void adminCancellationList_validatesPaginationAndStatus() throws Exception {
		currentUser.signInAsAdmin(admin);

		mockMvc.perform(get("/api/v1/admin/cancellation-requests").param("size", "15"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/api/v1/admin/cancellation-requests").param("status", "UNKNOWN"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unauthenticatedRequest_returns401() throws Exception {
		mockMvc.perform(get("/api/v1/admin/cancellation-requests"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void createAppeal_returns201AndRejectsDuplicate() throws Exception {
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, TaskStatus.COMPLETED, 30));
		currentUser.signInAsUser(publisher);

		mockMvc.perform(post("/api/v1/tasks/{taskId}/appeals", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"FULFILLMENT_ISSUE\",\"description\":\"物品不符\"}")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.appellantId").value(publisher.toString()))
				.andExpect(jsonPath("$.data.respondentId").value(runner.toString()))
				.andExpect(jsonPath("$.data.transactionIds").isEmpty());

		mockMvc.perform(post("/api/v1/tasks/{taskId}/appeals", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"OTHER\",\"description\":\"重复\"}")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPEAL_ALREADY_EXISTS"));
	}

	@Test
	void decideAppeal_returnsDecidedAppealWithTransferIds() throws Exception {
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, TaskStatus.COMPLETED, 30));
		currentUser.signInAsUser(publisher);
		String appealId = createAppeal();
		ledger.setBalance(runner, 20);

		currentUser.signInAsAdmin(admin);
		mockMvc.perform(post("/api/v1/admin/appeals/{appealId}/decision", appealId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"result\":\"SUPPORTED\",\"transferAmount\":10,\"decisionNote\":\"核实属实\"}")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DECIDED"))
				.andExpect(jsonPath("$.data.result").value("SUPPORTED"))
				.andExpect(jsonPath("$.data.transferAmount").value(10))
				.andExpect(jsonPath("$.data.transactionIds").isArray())
				.andExpect(jsonPath("$.data.transactionIds.length()").value(2));
	}

	@Test
	void decideAppeal_validatesRequiredFields() throws Exception {
		taskPort.put(new TaskSnapshot(taskId, publisher, runner, TaskStatus.COMPLETED, 30));
		currentUser.signInAsUser(publisher);
		String appealId = createAppeal();

		currentUser.signInAsAdmin(admin);
		mockMvc.perform(post("/api/v1/admin/appeals/{appealId}/decision", appealId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"result\":\"SUPPORTED\",\"transferAmount\":10,\"decisionNote\":\" \"}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private String createCancellationRequest() throws Exception {
		String body = mockMvc.perform(post("/api/v1/tasks/{taskId}/cancellation-requests", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"取消原因\"}")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return extractId(body, "requestId");
	}

	private String createAppeal() throws Exception {
		String body = mockMvc.perform(post("/api/v1/tasks/{taskId}/appeals", taskId)
						.contentType(MediaType.APPLICATION_JSON)
						.content(json("{\"reason\":\"OTHER\",\"description\":\"申诉说明\"}")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return extractId(body, "appealId");
	}

	private String json(String value) {
		return value;
	}

	private String extractId(String body, String field) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"")
				.matcher(body);
		if (!matcher.find()) {
			throw new IllegalStateException("响应中缺少 " + field + "：" + body);
		}
		return matcher.group(1);
	}
}
