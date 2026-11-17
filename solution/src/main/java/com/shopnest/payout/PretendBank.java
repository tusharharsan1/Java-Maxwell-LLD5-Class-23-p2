package com.shopnest.payout;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A pretend bank, for practice. It has three moods (see PretendController):
 *   ok      takes the payout and gives back a reference like BANK-1
 *   no      says no, with a reason (a GatewayException)
 *   silent  does not answer at all (a GatewayException too: the call timed out)
 * Each call writes one line in the console, so you can count the calls that really reach the bank.
 * Already finished: do not change this file.
 */
@Component
public class PretendBank implements BankApi {

    private volatile String mode = "ok";
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean lastCallInTransaction;

    public void setMode(String mode) {
        this.mode = mode;
    }

    @Override
    public String transfer(Payout payout) {
        int number = calls.incrementAndGet();
        lastCallInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        System.out.println("pretend bank: " + payout.getSellerId() + ", " + payout.getAmount() + " paise -> " + mode);
        return switch (mode) {
            case "no" -> throw new GatewayException("Bank answered 400: the shop account has too little money");
            case "silent" -> throw new GatewayException("Bank did not answer: read timed out");
            default -> "BANK-" + number;
        };
    }

    public int calls() {
        return calls.get();
    }

    /** true if a database transaction was open while the bank was called. It must never be. */
    public boolean lastCallInTransaction() {
        return lastCallInTransaction;
    }

    public void reset() {
        mode = "ok";
        calls.set(0);
        lastCallInTransaction = false;
    }
}
