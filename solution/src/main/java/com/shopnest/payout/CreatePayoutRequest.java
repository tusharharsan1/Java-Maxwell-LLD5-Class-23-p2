package com.shopnest.payout;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the caller sends. The amount is in paise, and at least 100 (1 rupee). Already finished. */
public record CreatePayoutRequest(
        @NotBlank @Size(max = 64) String sellerId,
        @Min(100) long amount) {
}
