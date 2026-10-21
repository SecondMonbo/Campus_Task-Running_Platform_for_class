package edu.campus.taskplatform.task;

import edu.campus.taskplatform.accounting.DemoAccountingService;
import edu.campus.taskplatform.auth.CurrentUser;
import edu.campus.taskplatform.auth.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 控制器层测试：验证 HTTP 接口成功路径、失败路径、认证与统一错误响应。
 * 当前用户由测试桩固定为 userId=1（发布者）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class TaskControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DemoAccountingService accounting;

    @TestConfiguration
    static class StubCurrentUser {
        @Bean
        @Primary
        CurrentUserService stubCurrentUserService() {
            return () -> new CurrentUser(1L, "USER");
        }
    }

    @BeforeEach
    void setUp() {
        accounting.reset();
        accounting.deposit(1L, 5000);
    }

    @Test
    void create_task_success_201() throws Exception {
        String body = """
                {"title":"代取资料","description":"到教学楼领取资料并送到宿舍区",
                 "reward":30,"deadline":"%s","pickupLocation":"教学楼A座","deliveryLocation":"宿舍区"}
                """.formatted(Instant.now().plus(2, ChronoUnit.HOURS).toString());
        mockMvc.perform(post("/api/v1/tasks")
                        .with(user("u1").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("WAITING_FOR_TAKER"))
                .andExpect(jsonPath("$.data.reward").value(30));
    }

    @Test
    void create_task_validation_400() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .with(user("u1").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"代取资料\",\"description\":\"x\"}")) // 缺 reward/deadline
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void list_tasks_200_empty_items() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                        .with(user("u1").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void publish_own_task_accept_is_forbidden_403() throws Exception {
        // 先发布一个任务（用户1是发布者）
        String body = """
                {"title":"代取资料","description":"到教学楼领取资料并送到宿舍区",
                 "reward":30,"deadline":"%s","pickupLocation":"教学楼A座","deliveryLocation":"宿舍区"}
                """.formatted(Instant.now().plus(2, ChronoUnit.HOURS).toString());
        String result = mockMvc.perform(post("/api/v1/tasks")
                        .with(user("u1").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String taskId = result.replaceAll(".*\"taskId\":\"([^\"]+)\".*", "$1");

        // 发布者接自己的单 → 403
        mockMvc.perform(post("/api/v1/tasks/{id}/acceptance", taskId)
                        .with(user("u1").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalid_size_returns_400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("size", "15")
                        .with(user("u1").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void get_missing_task_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/nope")
                        .with(user("u1").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
