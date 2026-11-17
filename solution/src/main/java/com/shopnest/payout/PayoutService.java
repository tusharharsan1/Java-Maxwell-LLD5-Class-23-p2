package com.shopnest.payout;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class PayoutService {

    private final PayoutRepo repo;
    private final BankApi bank;

    public PayoutService(PayoutRepo repo, BankApi bank) {
        this.repo = repo;
        this.bank = bank;
    }

    public Payout get(UUID id) {
        return find(id);
    }

    public List<Payout> list() {
        return repo.findAll();
    }

    // The bank confirmed the transfer: SENT -> PAID. Any other status refuses the move.
    public Payout confirm(UUID id) {
        Payout payout = find(id);
        payout.moveTo(PayoutStatus.PAID);
        return repo.save(payout);
    }

    // Somebody cancelled the payout: SENT -> CANCELLED. Any other status refuses the move.
    public Payout cancel(UUID id) {
        Payout payout = find(id);
        payout.moveTo(PayoutStatus.CANCELLED);
        return repo.save(payout);
    }

    // No database transaction stays open while we wait for the bank: short saves, and the call in between.
    public Payout create(CreatePayoutRequest request) {
        Payout payout = repo.save(new Payout(request.sellerId(), request.amount()));
        try {
            payout.setBankReference(bank.transfer(payout));
            payout.moveTo(PayoutStatus.SENT);
        } catch (GatewayException e) {
            payout.setFailureReason(e.getMessage());
            payout.moveTo(PayoutStatus.FAILED);
            repo.save(payout);
            throw e;
        }
        return repo.save(payout);
    }

    private Payout find(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchPayoutException(id));
    }
}
