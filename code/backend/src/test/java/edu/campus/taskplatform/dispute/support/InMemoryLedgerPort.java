package edu.campus.taskplatform.dispute.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;
import edu.campus.taskplatform.dispute.port.LedgerPort;

/**
 * 测试用账务端口：记录结算调用并模拟余额划转，便于断言“是否真的走账务端口”。
 */
public class InMemoryLedgerPort implements LedgerPort {

	private final Map<UUID, Integer> balances = new ConcurrentHashMap<>();
	private final List<Settlement> settlements = new CopyOnWriteArrayList<>();

	@Override
	public void settleFrozenReward(UUID taskId, UUID publisherId, UUID runnerId,
			int refundToPublisher, int payToRunner) {
		settlements.add(new Settlement(taskId, publisherId, runnerId, refundToPublisher, payToRunner));
	}

	@Override
	public List<UUID> transferAvailable(UUID taskId, UUID appealId, UUID fromUserId, UUID toUserId, int amount) {
		int fromBalance = availableBalance(fromUserId);
		if (fromBalance < amount) {
			throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
		}
		balances.put(fromUserId, fromBalance - amount);
		balances.put(toUserId, availableBalance(toUserId) + amount);
		return List.of(UUID.randomUUID(), UUID.randomUUID());
	}

	@Override
	public int availableBalance(UUID userId) {
		return balances.getOrDefault(userId, 0);
	}

	public void setBalance(UUID userId, int amount) {
		balances.put(userId, amount);
	}

	public List<Settlement> settlements() {
		return List.copyOf(settlements);
	}

	public void reset() {
		balances.clear();
		settlements.clear();
	}

	public record Settlement(UUID taskId, UUID publisherId, UUID runnerId, int refundToPublisher, int payToRunner) {
	}
}
