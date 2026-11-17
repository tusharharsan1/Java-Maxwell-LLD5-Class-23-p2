package com.shopnest.payout;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One payout to a seller. The amount is a whole number of paise (49900 means 499.00 rupees). */
@Entity
@Table(name = "payout")
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String sellerId;
    private long amount;

    // There is no setter for the status on purpose: it changes only through moveTo.
    @Enumerated(EnumType.STRING)
    private PayoutStatus status = PayoutStatus.REQUESTED;

    private String bankReference;
    private String failureReason;

    protected Payout() {
    }

    public Payout(String sellerId, long amount) {
        this.sellerId = sellerId;
        this.amount = amount;
    }

    // The only door for a change of status. It refuses a move that is not allowed.
    public void moveTo(PayoutStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("A payout cannot move from " + status + " to " + next);
        }
        status = next;
    }

    public UUID getId() {
        return id;
    }

    public String getSellerId() {
        return sellerId;
    }

    public long getAmount() {
        return amount;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
