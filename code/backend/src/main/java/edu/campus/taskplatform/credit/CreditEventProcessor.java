package edu.campus.taskplatform.credit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 信用分事件消费者。每条事件在独立事务中处理，失败时单独记录错误并保留事件供重试。
 * 处理顺序为“事件行 → 用户行”，与账务/任务模块的加锁顺序保持一致。
 */
@Component
public class CreditEventProcessor {

	private static final Logger log = LoggerFactory.getLogger(CreditEventProcessor.class);
	private static final int BATCH_SIZE = 100;

	private final CreditEventRepository eventRepository;
	private final CreditScoreService creditScoreService;
	private final TransactionTemplate requiresNew;
	private final boolean autoProcess;
	private final boolean retryEnabled;

	public CreditEventProcessor(CreditEventRepository eventRepository, CreditScoreService creditScoreService,
			PlatformTransactionManager transactionManager,
			@Value("${credit.events.auto-process:true}") boolean autoProcess,
			@Value("${credit.events.retry-enabled:true}") boolean retryEnabled) {
		this.eventRepository = eventRepository;
		this.creditScoreService = creditScoreService;
		this.requiresNew = new TransactionTemplate(transactionManager);
		this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.autoProcess = autoProcess;
		this.retryEnabled = retryEnabled;
	}

	public boolean isAutoProcessEnabled() {
		return autoProcess;
	}

	/** 处理当前所有待处理/失败事件；单个事件失败不影响其余事件。 */
	public void processPending() {
		if (!autoProcess) {
			return;
		}
		List<UUID> ids = eventRepository.findByStatusInOrderByCreatedAtAsc(
						List.of(CreditEventStatus.PENDING, CreditEventStatus.FAILED),
						PageRequest.of(0, BATCH_SIZE))
				.stream()
				.map(CreditEvent::getEventId)
				.toList();
		for (UUID id : ids) {
			processOne(id);
		}
	}

	@Scheduled(fixedDelayString = "${credit.events.retry-delay-ms:10000}")
	public void retryPending() {
		if (!retryEnabled || !autoProcess) {
			return;
		}
		processPending();
	}

	/** 在独立事务中处理一条事件；失败时用另一个独立事务记录失败信息。 */
	public void processOne(UUID eventId) {
		try {
			requiresNew.executeWithoutResult(status -> {
				CreditEvent event = eventRepository.findByIdForUpdate(eventId).orElse(null);
				if (event == null || event.getStatus() == CreditEventStatus.PROCESSED) {
					return;
				}
				creditScoreService.applyEvent(event.getUserId(), event.getEventType(),
						event.getSourceId(), event.getRequestedDelta(), event.getReason());
				event.markProcessed(Instant.now());
				eventRepository.save(event);
			});
		}
		catch (RuntimeException failure) {
			log.warn("信用分事件处理失败：event={}, reason={}", eventId, failure.getMessage());
			markFailed(eventId, failure);
		}
	}

	private void markFailed(UUID eventId, RuntimeException failure) {
		try {
			requiresNew.executeWithoutResult(status -> eventRepository.findByIdForUpdate(eventId)
					.ifPresent(event -> {
						event.markFailed(message(failure));
						eventRepository.save(event);
					}));
		}
		catch (RuntimeException nested) {
			log.error("信用分事件失败状态写入失败：event={}", eventId, nested);
		}
	}

	private static String message(RuntimeException failure) {
		String message = failure.getMessage();
		if (message == null || message.isBlank()) {
			return failure.getClass().getSimpleName();
		}
		return failure.getClass().getSimpleName() + ": " + message;
	}
}
