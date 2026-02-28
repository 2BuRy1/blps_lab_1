package com.example.bank.api

import com.example.bank.service.BankPaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BankController(
    private val bankPaymentService: BankPaymentService,
) {

    @PostMapping("/bank/pay")
    fun bankPay(@RequestBody request: BankPayRequest): ResponseEntity<BankPayResponse> {
        return ResponseEntity.ok(bankPaymentService.pay(request))
    }

    @PostMapping("/bank/pay/{paymentId}/confirm-3ds")
    fun confirm3ds(
        @PathVariable paymentId: String,
        @RequestBody request: Confirm3dsRequest,
    ): ResponseEntity<BankPayResponse> {
        return ResponseEntity.ok(bankPaymentService.confirm3ds(paymentId, request))
    }
}
