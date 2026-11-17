package com.shopnest.payout;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The remote control of the pretend bank, for practice:
 *   curl "localhost:8080/pretend/bank?mode=no"       the bank says no
 *   curl "localhost:8080/pretend/bank?mode=silent"   the bank does not answer
 *   curl "localhost:8080/pretend/bank?mode=ok"       the bank works again
 * Already finished: do not change this file.
 */
@RestController
public class PretendController {

    private final PretendBank bank;

    public PretendController(PretendBank bank) {
        this.bank = bank;
    }

    @GetMapping("/pretend/bank")
    public String set(@RequestParam(defaultValue = "ok") String mode) {
        bank.setMode(mode);
        return "bank is now: " + mode;
    }
}
