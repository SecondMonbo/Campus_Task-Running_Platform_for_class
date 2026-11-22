package edu.campus.taskplatform.credit;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在业务事务内可靠写入信用分待处理事件。事件提交后立即尝试消费一次；
 * 若消费失败，事件保留并由定时任务重试。同一 (eventType, sourceId) 只落库一次。
 */
@Component
public class CreditEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(CreditEventPublisher.class);

	private final CreditEventRepository repository;
	private final CreditEventProcessor processor;

	public CreditEventPublisher(CreditEventRepository repository, CreditEventProcessor processor) {
		this.repository = repository;
		this.processor = processor;
	}

	/**
	 * 记录一条信用分事件。重复事件（同一业务资源）被唯一约束拦截后静默忽略。
	 */
	public void publish(Long userId, CreditEventType eventType, String sourceId, int requestedDelta,
			String reason) {
		try {
			repository.saveAndFlush(new CreditEvent(userId, eventType, sourceId, requestedDelta,
					reason, Instant.now()));
		}
		catch (DataIntegrityViolationException duplicate) {
			// 同一业务资源的重复事件：已处理或已在队列中，保持幂等。
			return;
		}
		triggerProcessing();
	}

	private void triggerProcessing() {
		if (!processor.isAutoProcessEnabled()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			processor.processPending();
			return;
		}
		if (TransactionSynchronizationManager.hasResource(this)) {
			return;
		}
		CreditEventPublisher publisher = this;
		TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					processor.processPending();
				}
				catch (RuntimeException failure) {
					log.warn("信用分事件即时消费失败，等待定时重试：{}", failure.getMessage());
				}
			}

			@Override
			public void afterCompletion(int status) {
				TransactionSynchronizationManager.unbindResourceIfPossible(publisher);
			}
		});
	}
}
