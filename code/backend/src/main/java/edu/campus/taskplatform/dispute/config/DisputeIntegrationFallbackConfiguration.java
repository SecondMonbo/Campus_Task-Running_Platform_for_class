package edu.campus.taskplatform.dispute.config;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.port.CurrentUserPort;
import edu.campus.taskplatform.dispute.port.LedgerPort;
import edu.campus.taskplatform.dispute.port.TaskParticipationPort;
import edu.campus.taskplatform.dispute.port.TaskSnapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 取消/申诉模块的集成占位配置。
 *
 * <p>本模块只依赖 {@link CurrentUserPort}、{@link TaskParticipationPort} 和 {@link LedgerPort}
 * 三个端口。登录、任务与账务公共能力尚未合并时，这里提供“失败关闭”的占位实现，保证应用能启动、
 * 已有接口不受影响；一旦组长或后端 A 注册了真实实现，下列 Bean 因
 * {@link ConditionalOnMissingBean} 自动失效。</p>
 *
 * <p>这是临时配置，集成完成后可整体删除。</p>
 */
@AutoConfiguration
public class DisputeIntegrationFallbackConfiguration {

	private static final Logger log = LoggerFactory.getLogger(DisputeIntegrationFallbackConfiguration.class);

	/**
	 * 未接入登录模块时视为未登录，所有取消/申诉接口返回 401。
	 */
	@Bean
	@ConditionalOnMissingBean(CurrentUserPort.class)
	CurrentUserPort unauthenticatedCurrentUserPort() {
		log.warn("Cancellation/Appeal module: CurrentUserPort is not provided yet; all dispute endpoints will return 401");
		return Optional::empty;
	}

	@Bean
	@ConditionalOnMissingBean(TaskParticipationPort.class)
	TaskParticipationPort unavailableTaskParticipationPort() {
		log.warn("Cancellation/Appeal module: TaskParticipationPort is not provided yet; task-dependent operations will fail");
		return new TaskParticipationPort() {
			@Override
			public Optional<TaskSnapshot> findTaskForUpdate(String taskId) {
				throw notReady();
			}

			@Override
			public void cancelTask(String taskId) {
				throw notReady();
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean(LedgerPort.class)
	LedgerPort unavailableLedgerPort() {
		log.warn("Cancellation/Appeal module: LedgerPort is not provided yet; settlement operations will fail");
		return new LedgerPort() {
			@Override
			public void settleFrozenReward(String taskId, Long publisherId, Long runnerId,
					long refundToPublisher, long payToRunner) {
				throw notReady();
			}

			@Override
			public List<UUID> transferAvailable(String taskId, UUID appealId,
					Long fromUserId, Long toUserId, long amount) {
				throw notReady();
			}

			@Override
			public long availableBalance(Long userId) {
				throw notReady();
			}
		};
	}

	private static ApiException notReady() {
		return new ApiException(ErrorCode.INTEGRATION_NOT_READY, "任务或账务公共能力尚未接入");
	}
}
