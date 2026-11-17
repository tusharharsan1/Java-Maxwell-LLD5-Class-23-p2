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
        // TODO 1: write the rules of the picture above, with a switch on `this` (one case for each status that has
        //         a way out; every other status is final). Return true only for a move that is on an arrow.
        return false;
    }
}
