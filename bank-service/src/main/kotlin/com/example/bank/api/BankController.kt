package com.example.bank.api

import com.example.bank.service.BankPaymentService
import org.springframework.http.ResponseEntity
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
}
