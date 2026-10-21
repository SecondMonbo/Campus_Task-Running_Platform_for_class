package edu.campus.taskplatform.accounting;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时内存账务实现：用于本地联调与单元/集成测试。
 *
 * <p>这不是生产账务。组长账务服务就绪后，用真实实现替换本 Bean
 * （实现 {@link AccountingService} 并以 @Primary 注册），任务模块代码无需改动。</p>
 *
 * <p>注意：本实现不落库、不真正加入数据库事务。生产环境必须以组长提供的、
 * 与任务状态在同一事务提交的账务服务为准。</p>
 */
@Component
public class DemoAccountingService implements AccountingService {

    private final Map<Long, long[]> balances = new ConcurrentHashMap<>();

    /** 初始化用户余额（仅供联调与测试预充值）。 */
    public void deposit(long userId, long amount) {
        balances.computeIfAbsent(userId, k -> new long[2])[0] += amount;
    }

    /** 清空全部余额状态（测试隔离用）。 */
    public void reset() {
        balances.clear();
    }

    @Override
    public void freeze(long userId, String taskId, long amount) {
        long[] b = balances.computeIfAbsent(userId, k -> new long[2]);
        synchronized (b) {
            if (b[0] < amount) {
                throw new InsufficientBalanceException("余额不足");
            }
            b[0] -= amount;
            b[1] += amount;
        }
    }

    @Override
    public void unfreeze(long userId, String taskId, long amount) {
        long[] b = balances.computeIfAbsent(userId, k -> new long[2]);
        synchronized (b) {
            b[1] = Math.max(0, b[1] - amount);
            b[0] += amount;
        }
    }

    @Override
    public void adjustFrozen(long userId, String taskId, long delta) {
        if (delta > 0) {
            freeze(userId, taskId, delta);
        } else if (delta < 0) {
            unfreeze(userId, taskId, -delta);
        }
    }

    @Override
    public void settle(String taskId, long publisherId, long runnerId, long amount) {
        long[] pub = balances.computeIfAbsent(publisherId, k -> new long[2]);
        synchronized (pub) {
            if (pub[1] < amount) {
                throw new InsufficientBalanceException("发布者冻结余额不足，无法结算");
            }
            pub[1] -= amount;
        }
        long[] run = balances.computeIfAbsent(runnerId, k -> new long[2]);
        synchronized (run) {
            run[0] += amount;
        }
    }

    @Override
    public long getAvailableBalance(long userId) {
        long[] b = balances.get(userId);
        return b == null ? 0 : b[0];
    }

    @Override
    public long getFrozenBalance(long userId) {
        long[] b = balances.get(userId);
        return b == null ? 0 : b[1];
    }
}
