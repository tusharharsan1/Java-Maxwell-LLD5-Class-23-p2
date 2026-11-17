package com.shopnest.payout;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Already finished: do not change this file. */
@RestController
@RequestMapping("/payouts")
public class PayoutController {

    private final PayoutService service;

    public PayoutController(PayoutService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayoutDto create(@Valid @RequestBody CreatePayoutRequest request) {
        return PayoutDto.from(service.create(request));
    }

    @GetMapping
    public List<PayoutDto> list() {
        return service.list().stream().map(PayoutDto::from).toList();
    }

    @GetMapping("/{id}")
    public PayoutDto get(@PathVariable UUID id) {
        return PayoutDto.from(service.get(id));
    }

    @PostMapping("/{id}/confirm")
    public PayoutDto confirm(@PathVariable UUID id) {
        return PayoutDto.from(service.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    public PayoutDto cancel(@PathVariable UUID id) {
        return PayoutDto.from(service.cancel(id));
    }
}
