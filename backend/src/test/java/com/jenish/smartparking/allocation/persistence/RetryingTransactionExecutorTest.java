package com.jenish.smartparking.allocation.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class RetryingTransactionExecutorTest {

    private final RetryingTransactionExecutor executor =
            new RetryingTransactionExecutor(new DirectTransactionOperations());

    @Test
    void retriesTransientFailuresWithinTheConfiguredLimit() {
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < RetryingTransactionExecutor.MAXIMUM_ATTEMPTS) {
                throw new TransientDataAccessResourceException("temporary failure");
            }
            return "completed";
        });

        assertEquals("completed", result);
        assertEquals(RetryingTransactionExecutor.MAXIMUM_ATTEMPTS, attempts.get());
    }

    @Test
    void propagatesTheLastTransientFailureAfterTheRetryLimit() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(TransientDataAccessResourceException.class, () -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new TransientDataAccessResourceException("persistent failure");
        }));
        assertEquals(RetryingTransactionExecutor.MAXIMUM_ATTEMPTS, attempts.get());
    }

    private static final class DirectTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }
}
