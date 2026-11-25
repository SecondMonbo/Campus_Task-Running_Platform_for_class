package edu.campus.taskplatform.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import edu.campus.taskplatform.task.Task;
import edu.campus.taskplatform.task.TaskRepository;
import edu.campus.taskplatform.task.TaskStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
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

/**
 * 信用分集成测试：初始化 80 分、评价/按时/超时事件、30 天低星规则、幂等、上下限截断，
 * 以及本人查询与他人主页的可见性边界。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class CreditScoreIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CreditScoreService creditScoreService;
    @Autowired CreditScoreRecordRepository recordRepository;
    @Autowired AppUserRepository users;
    @Autowired TaskRepository tasks;

    Long publisherId;
    Long runnerId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        publisherId = users.saveAndFlush(new AppUser("cpub-" + suffix, "unused-test-hash",
                "发布者", null, Instant.now())).getUserId();
        runnerId = users.saveAndFlush(new AppUser("crun-" + suffix, "unused-test-hash",
                "跑腿员", null, Instant.now())).getUserId();
    }

    @Test
    void newUserStartsWith80AndInitializationRecord() {
        assertEquals(80, creditScoreService.scoreOf(runnerId));

        List<CreditScoreRecord> records = recordRepository
                .findByUserId(runnerId, PageRequest.of(0, 10)).getContent();
        assertEquals(1, records.size());
        CreditScoreRecord baseline = records.get(0);
        assertEquals(CreditEventType.INITIALIZATION, baseline.getEventType());
        assertNull(baseline.getScoreBefore());
        assertEquals(0, baseline.getRequestedDelta());
        assertEquals(0, baseline.getActualDelta());
        assertEquals(80, baseline.getScoreAfter());
    }

    @Test
    void reviewEventsFollowRulesAndAreIdempotent() {
        creditScoreService.applyReview(publisherId, runnerId, "review-5star", 5, true, "任务A");
        assertEquals(82, creditScoreService.scoreOf(runnerId));

        // 同一评价重复投递：不重复加分。
        creditScoreService.applyReview(publisherId, runnerId, "review-5star", 5, true, "任务A");
        assertEquals(82, creditScoreService.scoreOf(runnerId));

        // 3–4 星：只记零分值记录，不加减分。
        creditScoreService.applyReview(publisherId, runnerId, "review-4star", 4, true, "任务B");
        assertEquals(82, creditScoreService.scoreOf(runnerId));
        CreditScoreRecord neutral = findRecord("review-4star");
        assertEquals(0, neutral.getRequestedDelta());
        assertEquals(0, neutral.getActualDelta());

        // 1–2 星：30 天内首次扣 -3。
        creditScoreService.applyReview(publisherId, runnerId, "review-2star-first", 2, true, "任务C");
        assertEquals(79, creditScoreService.scoreOf(runnerId));
        CreditScoreRecord firstLow = findRecord("review-2star-first");
        assertEquals(-3, firstLow.getActualDelta());

        // 同一发布者对同一跑腿员 30 天内再次低星：仅留痕，不再扣分。
        creditScoreService.applyReview(publisherId, runnerId, "review-1star-later", 1, false, "任务D");
        assertEquals(79, creditScoreService.scoreOf(runnerId));
        assertEquals(0, findRecord("review-1star-later").getActualDelta());
    }

    @Test
    void completionEventsAddOneOnTimeAndSubtractFiveWhenOverdueBeyondGrace() {
        Task onTime = completedTask(Instant.now().plusSeconds(3600), Instant.now());
        creditScoreService.applyCompletionOutcome(onTime);
        assertEquals(81, creditScoreService.scoreOf(runnerId));

        // 截止后 24 小时内送达：不加不扣。
        Task withinGrace = completedTask(Instant.now().minusSeconds(30 * 3600),
                Instant.now().minusSeconds(29 * 3600));
        creditScoreService.applyCompletionOutcome(withinGrace);
        assertEquals(81, creditScoreService.scoreOf(runnerId));
        assertNull(findRecordOrNull(withinGrace.getTaskId()));

        // 超时 24 小时以上送达：-5，每任务一次。
        Task overtime = completedTask(Instant.now().minusSeconds(30 * 3600),
                Instant.now().minusSeconds(5 * 3600));
        creditScoreService.applyCompletionOutcome(overtime);
        assertEquals(76, creditScoreService.scoreOf(runnerId));
        creditScoreService.applyCompletionOutcome(overtime);
        assertEquals(76, creditScoreService.scoreOf(runnerId));
        assertEquals(-5, findRecord(overtime.getTaskId()).getActualDelta());
    }

    @Test
    void scoreIsClampedToRangeAndZeroDeltaIsStillRecorded() {
        creditScoreService.applyEvent(runnerId, CreditEventType.ON_TIME_COMPLETION, "clamp-up", 50,
                "上限截断");
        assertEquals(100, creditScoreService.scoreOf(runnerId));
        CreditScoreRecord up = findRecord("clamp-up");
        assertEquals(50, up.getRequestedDelta());
        assertEquals(20, up.getActualDelta());

        creditScoreService.applyEvent(runnerId, CreditEventType.OVERTIME_DELIVERY, "clamp-down", -100,
                "下限截断");
        assertEquals(0, creditScoreService.scoreOf(runnerId));
        CreditScoreRecord down = findRecord("clamp-down");
        assertEquals(-100, down.getActualDelta());

        creditScoreService.applyEvent(runnerId, CreditEventType.REVIEW_NEUTRAL, "zero-event", 0,
                "零分值留痕");
        assertEquals(0, creditScoreService.scoreOf(runnerId));
        assertEquals(0, findRecord("zero-event").getActualDelta());
    }

    @Test
    void myCreditEndpointReturnsScoreTierAndRecords() throws Exception {
        mvc.perform(get("/api/v1/users/me/credit").session(session(runnerId)).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.score").value(80))
                .andExpect(jsonPath("$.data.tier").value("GOOD"))
                .andExpect(jsonPath("$.data.tierLabel").value("良好"))
                .andExpect(jsonPath("$.data.permissionHint").isString())
                .andExpect(jsonPath("$.data.recoveryHint").isString())
                .andExpect(jsonPath("$.data.records.total").value(1))
                .andExpect(jsonPath("$.data.records.items[0].eventType").value("INITIALIZATION"))
                .andExpect(jsonPath("$.data.records.items[0].scoreAfter").value(80));
    }

    @Test
    void publicProfileOnlyExposesTierAndCompletedCount() throws Exception {
        Task finished = completedTask(Instant.now().plusSeconds(3600), Instant.now());
        finished.setStatus(TaskStatus.COMPLETED);
        tasks.saveAndFlush(finished);

        mvc.perform(get("/api/v1/users/{userId}/profile", runnerId).session(session(publisherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(runnerId.toString()))
                .andExpect(jsonPath("$.data.displayName").value("跑腿员"))
                .andExpect(jsonPath("$.data.creditTier").value("GOOD"))
                .andExpect(jsonPath("$.data.creditTierLabel").value("良好"))
                .andExpect(jsonPath("$.data.completedTasks").value(1))
                // 他人主页不得出现精确分值与变更明细。
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andExpect(jsonPath("$.data.records").doesNotExist());
    }

    @Test
    void creditEndpointsValidateAuthPagingAndUnknownUser() throws Exception {
        mvc.perform(get("/api/v1/users/me/credit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/{userId}/profile", publisherId))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/users/me/credit").session(session(runnerId)).param("size", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/users/me/credit").session(session(runnerId)).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/v1/users/{userId}/profile", 999_999L).session(session(runnerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/v1/users/not-a-number/profile").session(session(runnerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void taskReviewEndpointAppliesFiveStarBonus() throws Exception {
        Task finished = completedTask(Instant.now().plusSeconds(3600), Instant.now());
        finished.setStatus(TaskStatus.COMPLETED);
        tasks.saveAndFlush(finished);

        mvc.perform(post("/api/v1/tasks/{taskId}/reviews", finished.getTaskId())
                        .session(session(publisherId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5,\"content\":\"非常准时\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.score").value(5));

        mvc.perform(get("/api/v1/users/me/credit").session(session(runnerId)).param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(82))
                .andExpect(jsonPath("$.data.records.total").value(2));
    }

    // ---------------------------------------------------------------
    // 辅助
    // ---------------------------------------------------------------

    private Task completedTask(Instant deadline, Instant deliveredAt) {
        Task task = new Task(UUID.randomUUID().toString(), publisherId, "信用分测试任务", "说明", 20,
                deadline, "取件点", "送达点", Instant.now().minusSeconds(3600));
        task.setRunnerId(runnerId);
        task.setStatus(TaskStatus.WAITING_FOR_ACCEPTANCE);
        task.setDeliveredAt(deliveredAt);
        return tasks.saveAndFlush(task);
    }

    private CreditScoreRecord findRecord(String sourceId) {
        CreditScoreRecord record = findRecordOrNull(sourceId);
        assertNotNull(record, "缺少 sourceId=" + sourceId + " 的信用分记录");
        return record;
    }

    private CreditScoreRecord findRecordOrNull(String sourceId) {
        return recordRepository.findAll().stream()
                .filter(item -> sourceId.equals(item.getSourceId()))
                .findFirst().orElse(null);
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
