package edu.campus.taskplatform.accounting;

/**
 * 余额不足异常。由 GlobalExceptionHandler 映射为 INSUFFICIENT_BALANCE（409）。
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
