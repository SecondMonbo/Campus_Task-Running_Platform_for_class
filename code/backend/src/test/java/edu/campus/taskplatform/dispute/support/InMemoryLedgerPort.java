package edu.campus.taskplatform.dispute.support;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.port.LedgerPort;

/**
 * 测试用账务端口：记录结算调用并模拟余额划转，便于断言“是否真的走账务端口”。
 */
public class InMemoryLedgerPort implements LedgerPort {

	private final Map<Long, Long> balances = new ConcurrentHashMap<>();
	private final List<Settlement> settlements = new CopyOnWriteArrayList<>();
	private final AtomicReference<TransferOutcome> nextTransferOutcome =
			new AtomicReference<>(TransferOutcome.NORMAL);

	/**
	 * 覆盖下一次 {@link #transferAvailable} 的返回结果，用于模拟账务服务的异常返回值。
	 * 默认 {@link TransferOutcome#NORMAL} 表示按原有逻辑正常划转。
	 */
	public enum TransferOutcome {
		NORMAL, NULL_IDS, EMPTY_IDS, TOO_MANY_IDS
	}

	public void overrideNextTransfer(TransferOutcome outcome) {
		nextTransferOutcome.set(Objects.requireNonNull(outcome));
	}

	@Override
	public void settleFrozenReward(String taskId, Long publisherId, Long runnerId,
			long refundToPublisher, long payToRunner) {
		settlements.add(new Settlement(taskId, publisherId, runnerId, refundToPublisher, payToRunner));
	}

	@Override
	public List<UUID> transferAvailable(String taskId, UUID appealId,
			Long fromUserId, Long toUserId, long amount) {
		TransferOutcome outcome = nextTransferOutcome.getAndSet(TransferOutcome.NORMAL);
		switch (outcome) {
			case NULL_IDS:
				return null;
			case EMPTY_IDS:
				return List.of();
			case TOO_MANY_IDS:
				return List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
			default:
				break;
		}
		long fromBalance = availableBalance(fromUserId);
		if (fromBalance < amount) {
			throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
		}
		balances.put(fromUserId, fromBalance - amount);
		balances.put(toUserId, availableBalance(toUserId) + amount);
		return List.of(UUID.randomUUID(), UUID.randomUUID());
	}

	@Override
	public long availableBalance(Long userId) {
		return balances.getOrDefault(userId, 0L);
	}

	public void setBalance(Long userId, long amount) {
		balances.put(userId, amount);
	}

	public List<Settlement> settlements() {
		return List.copyOf(settlements);
	}

	public void reset() {
		balances.clear();
		settlements.clear();
		nextTransferOutcome.set(TransferOutcome.NORMAL);
	}

	public record Settlement(String taskId, Long publisherId, Long runnerId,
			long refundToPublisher, long payToRunner) {
	}
}
