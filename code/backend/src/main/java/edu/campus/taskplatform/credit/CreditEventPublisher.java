package edu.campus.taskplatform.credit;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在业务事务内可靠写入信用分待处理事件。事件提交后立即尝试消费一次；
 * 若消费失败，事件保留并由定时任务重试。同一 (eventType, sourceId) 只落库一次。
 *
 * <p>重复事件使用数据库原子 upsert（PostgreSQL {@code ON CONFLICT DO NOTHING}，
 * H2 {@code MERGE … WHEN NOT MATCHED}）处理，而不是捕获唯一约束异常：
 * 后者会让 Hibernate 把当前事务标记为 rollback-only，导致外层任务/投诉事务在提交时失败。</p>
 */
@Component
public class CreditEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(CreditEventPublisher.class);

	private static final String POSTGRES_INSERT = """
			insert into credit_event
			    (event_id, user_id, event_type, source_id, reason, requested_delta,
			     status, attempts, last_error, created_at, processed_at)
			values (?, ?, ?, ?, ?, ?, 'PENDING', 0, null, ?, null)
			on conflict (event_type, source_id) do nothing
			""";

	private static final String H2_MERGE = """
			MERGE INTO credit_event AS t
			USING (VALUES (
			    CAST(? AS UUID), CAST(? AS BIGINT), CAST(? AS VARCHAR), CAST(? AS VARCHAR),
			    CAST(? AS VARCHAR), CAST(? AS INTEGER), CAST('PENDING' AS VARCHAR),
			    CAST(0 AS INTEGER), CAST(NULL AS VARCHAR),
			    CAST(? AS TIMESTAMP WITH TIME ZONE), CAST(NULL AS TIMESTAMP WITH TIME ZONE)))
			AS s(event_id, user_id, event_type, source_id, reason, requested_delta,
			     status, attempts, last_error, created_at, processed_at)
			ON t.event_type = s.event_type AND t.source_id = s.source_id
			WHEN NOT MATCHED THEN INSERT
			    (event_id, user_id, event_type, source_id, reason, requested_delta,
			     status, attempts, last_error, created_at, processed_at)
			VALUES (s.event_id, s.user_id, s.event_type, s.source_id, s.reason,
			        s.requested_delta, s.status, s.attempts, s.last_error,
			        s.created_at, s.processed_at)
			""";

	private final JdbcTemplate jdbc;
	private final CreditEventProcessor processor;
	private final String insertSql;
	private final boolean postgres;

	public CreditEventPublisher(JdbcTemplate jdbc, DataSource dataSource, CreditEventProcessor processor) {
		this.jdbc = jdbc;
		this.processor = processor;
		String product;
		try (Connection connection = dataSource.getConnection()) {
			product = connection.getMetaData().getDatabaseProductName();
		}
		catch (SQLException failure) {
			throw new IllegalStateException("无法识别数据库类型，信用分 outbox 无法写入", failure);
		}
		this.postgres = "PostgreSQL".equalsIgnoreCase(product);
		if (!postgres && !"H2".equalsIgnoreCase(product)) {
			throw new IllegalStateException("不支持的数据库类型：" + product);
		}
		this.insertSql = postgres ? POSTGRES_INSERT : H2_MERGE;
	}

	/**
	 * 记录一条信用分事件。重复事件（同一业务资源）由数据库原子忽略。
	 */
	public void publish(Long userId, CreditEventType eventType, String sourceId, int requestedDelta,
			String reason) {
		if (userId == null || eventType == null || sourceId == null || sourceId.isBlank()) {
			throw new IllegalArgumentException("信用分事件必须包含用户、类型与业务资源 ID");
		}
		String safeSourceId = sourceId.length() > 100 ? sourceId.substring(0, 100) : sourceId;
		String safeReason = reason == null ? "" : reason;
		if (safeReason.length() > 500) {
			safeReason = safeReason.substring(0, 500);
		}
		UUID eventId = UUID.randomUUID();
		OffsetDateTime createdAt = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
		jdbc.update(insertSql, eventId, userId, eventType.name(), safeSourceId, safeReason,
				requestedDelta, createdAt);
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
