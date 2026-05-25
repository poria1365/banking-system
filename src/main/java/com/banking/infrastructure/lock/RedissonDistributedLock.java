package com.banking.infrastructure.lock;

import com.banking.domain.port.out.DistributedLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Production distributed lock backed by Redis via Redisson.
 *
 * Why Redisson over plain Redis SETNX?
 *   - RedissonMultiLock acquires all required locks atomically as a group.
 *   - Automatic lease expiry (leaseSeconds) releases locks if the JVM crashes
 *     mid-saga — preventing deadlock across restarts.
 *   - Redlock algorithm can be enabled for multi-node Redis HA (see RedissonConfig).
 *
 * Double-spend protection layers:
 *   1. This lock prevents concurrent transfers on the same accounts (distributed).
 *   2. @Version on AccountEntity catches races that slip past the lock.
 *
 * Sorted lock acquisition:
 *   All account IDs are sorted before locking. Thread A locking (Alice, Bob) and
 *   Thread B locking (Bob, Alice) both end up acquiring Alice-then-Bob, so neither
 *   can deadlock waiting for the other.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedissonDistributedLock implements DistributedLockPort {

    private static final String LOCK_PREFIX = "banking:lock:account:";
    private static final long WAIT_SECONDS  = 10L;

    private final RedissonClient redissonClient;

    @Value("${banking.distributed-lock.default-lease-seconds:30}")
    private long leaseSeconds;

    @Override
    public void executeWithLocks(List<UUID> resourceIds, Runnable action) {
        if (resourceIds.isEmpty()) {
            action.run();
            return;
        }

        // Sort to guarantee consistent acquisition order across all instances
        List<RLock> locks = resourceIds.stream()
            .map(UUID::toString)
            .sorted()
            .map(id -> redissonClient.getLock(LOCK_PREFIX + id))
            .collect(Collectors.toList());

        RLock[] lockArray = locks.toArray(new RLock[0]);

        // RedissonMultiLock: acquires all locks or none (all-or-nothing semantics)
        RLock multiLock = redissonClient.getMultiLock(lockArray);

        try {
            boolean acquired = multiLock.tryLock(WAIT_SECONDS, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockAcquisitionException(
                    "Timed out acquiring distributed locks for accounts: " + resourceIds);
            }
            log.debug("Redis multi-lock acquired for accounts: {}", resourceIds);
            action.run();

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(
                "Interrupted while acquiring Redis locks", ex);
        } finally {
            if (multiLock.isHeldByCurrentThread()) {
                multiLock.unlock();
                log.debug("Redis multi-lock released for accounts: {}", resourceIds);
            }
        }
    }

    @Override
    public LockHandle tryLock(String resourceKey, long timeoutSeconds) {
        RLock rLock = redissonClient.getLock(LOCK_PREFIX + resourceKey);
        boolean acquired;
        try {
            acquired = rLock.tryLock(timeoutSeconds, leaseSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            acquired = false;
        }

        boolean finalAcquired = acquired;
        return new LockHandle() {
            @Override public boolean isAcquired() { return finalAcquired; }
            @Override public void close() {
                if (finalAcquired && rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                }
            }
        };
    }
}
