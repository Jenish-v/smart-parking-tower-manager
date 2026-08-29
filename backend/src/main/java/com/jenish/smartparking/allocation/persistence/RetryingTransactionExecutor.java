package com.jenish.smartparking.allocation.persistence;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

final class RetryingTransactionExecutor {

    static final int MAXIMUM_ATTEMPTS = 3;

    private final TransactionOperations transactions;

    RetryingTransactionExecutor(PlatformTransactionManager transactionManager) {
        this(new TransactionTemplate(transactionManager));
    }

    RetryingTransactionExecutor(TransactionOperations transactions) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
    }

    <T> T execute(Supplier<T> work) {
        Objects.requireNonNull(work, "work must not be null");
        TransientDataAccessException lastFailure = null;
        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status -> work.get());
            } catch (TransientDataAccessException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }
}
