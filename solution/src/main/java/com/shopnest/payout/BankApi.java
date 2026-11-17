package com.shopnest.payout;

/**
 * The bank is a "friend" of our shop: we call it to send money to a seller.
 * It gives back the bank's own reference for the transfer, or it throws a GatewayException.
 * Already finished: do not change this file.
 */
public interface BankApi {

    String transfer(Payout payout);
}
