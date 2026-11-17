package com.shopnest.payout;

/**
 * One error for every problem with the bank: the bank said no, or the bank did not answer.
 * Already finished: do not change this file.
 */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }
}
