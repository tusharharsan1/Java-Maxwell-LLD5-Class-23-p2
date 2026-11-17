package com.shopnest.payout;

import java.util.UUID;

/** What we send back. Already finished. */
public record PayoutDto(UUID id, String sellerId, long amount, String status, String bankReference,
                        String failureReason) {

    public static PayoutDto from(Payout p) {
        return new PayoutDto(p.getId(), p.getSellerId(), p.getAmount(), p.getStatus().name(),
                p.getBankReference(), p.getFailureReason());
    }
}
