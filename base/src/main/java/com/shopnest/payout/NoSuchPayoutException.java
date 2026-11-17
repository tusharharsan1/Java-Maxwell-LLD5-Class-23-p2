package com.shopnest.payout;

import java.util.UUID;

/** Already finished: do not change this file. */
public class NoSuchPayoutException extends RuntimeException {

    public NoSuchPayoutException(UUID id) {
        super("No payout with the id " + id);
    }
}
