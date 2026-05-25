package com.banking.domain.port.out;

import java.util.List;
import java.util.UUID;

// Outbound port for distributed locking. The domain defines what it needs;
// the infrastructure layer (Redisson) does the actual Redis coordination.
public interface DistributedLockPort {

    // Acquires locks on all account IDs (caller should sort them first to prevent deadlock),
    // runs the action, then releases. Throws LockAcquisitionException if wait times out.
    void executeWithLocks(List<UUID> resourceIds, Runnable action);

    // Single-resource variant — caller is responsible for closing the returned handle.
    LockHandle tryLock(String resourceKey, long timeoutSeconds);

    interface LockHandle extends AutoCloseable {
        boolean isAcquired();
        @Override
        void close();
    }
}
