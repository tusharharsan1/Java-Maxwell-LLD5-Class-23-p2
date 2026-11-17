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

    public Payout create(CreatePayoutRequest request) {
        Payout payout = repo.save(new Payout(request.sellerId(), request.amount()));   // saved as REQUESTED
        // TODO 3: send the money. Call bank.transfer(payout) and keep the reference that it gives back
        //         (payout.setBankReference(...)). Then move the payout to SENT, and save it.
        //         If the bank throws a GatewayException (it said no, or it did not answer): write the message in the
        //         payout (setFailureReason), move it to FAILED, save it, and throw the same exception again.
        //         Catch only GatewayException. Call the bank only once. Do not put @Transactional on this method:
        //         no database transaction may stay open while we wait for the bank.
        return payout;
    }

    private Payout find(UUID id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchPayoutException(id));
    }
}
