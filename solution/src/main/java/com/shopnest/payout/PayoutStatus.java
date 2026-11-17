package com.shopnest.payout;

/**
 * The steps of a payout:
 *   REQUESTED -> SENT or FAILED       (we saved it, then the bank took it, or the bank said no)
 *   SENT      -> PAID or CANCELLED    (the bank confirmed it, or the payout was cancelled)
 *   PAID, CANCELLED and FAILED are final: nothing moves out of them.
 */
public enum PayoutStatus {
    REQUESTED, SENT, PAID, CANCELLED, FAILED;

    public boolean canMoveTo(PayoutStatus next) {
        return switch (this) {
            case REQUESTED -> next == SENT || next == FAILED;
            case SENT -> next == PAID || next == CANCELLED;
            default -> false; // PAID, CANCELLED and FAILED are final
        };
    }
}
