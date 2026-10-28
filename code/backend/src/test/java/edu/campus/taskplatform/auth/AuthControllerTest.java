package edu.campus.taskplatform.auth;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.campus.taskplatform.accounting.WalletAccountRepository;
import edu.campus.taskplatform.accounting.WalletTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppUserRepository userRepository;

	@Autowired
	WalletAccountRepository accountRepository;

	@Autowired
	WalletTransactionRepository transactionRepository;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void registerCreatesSessionAndGrantsInitialWalletBalance() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"student1","password":"password-123","displayName":"林同学","phone":"123"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.userId").isString())
				.andExpect(jsonPath("$.data.username").value("student1"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andReturn();

		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertEquals(1, userRepository.count());
		Long userId = userRepository.findByUsername("student1").orElseThrow().getUserId();
		assertNotEquals("password-123",
				userRepository.findById(userId).orElseThrow().getPasswordHash());
		assertEquals(100, accountRepository.findById(userId).orElseThrow().getAvailableBalance());
		assertEquals(1, transactionRepository.count());

		mockMvc.perform(get("/api/v1/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(userId.toString()))
				.andExpect(jsonPath("$.data.displayName").value("林同学"));
	}

	@Test
	void duplicateUsernameIsRejectedWithoutSecondWallet() throws Exception {
		register("student2");

		mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"student2","password":"password-123","displayName":"另一同学"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_ACTION"));

		assertEquals(1, userRepository.count());
		assertEquals(1, accountRepository.count());
	}

	@Test
	void loginAndLogoutUseServerSession() throws Exception {
		MvcResult registration = register("student3");
		MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

		mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").doesNotExist());

		MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"student3","password":"password-123"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andReturn();

		MockHttpSession loginSession = (MockHttpSession) login.getRequest().getSession(false);
		mockMvc.perform(get("/api/v1/auth/me").session(loginSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.username").value("student3"));

		mockMvc.perform(post("/api/v1/auth/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"student3","password":"wrong-password"}
								"""))
				.andExpect(status().isUnauthorized());
	}

	private MvcResult register(String username) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"username":"%s","password":"password-123","displayName":"测试用户"}
								""".formatted(username)))
				.andExpect(status().isCreated())
				.andReturn();
	}
}
