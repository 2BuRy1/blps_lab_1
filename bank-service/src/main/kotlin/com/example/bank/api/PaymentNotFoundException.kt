package com.example.bank.api

class PaymentNotFoundException(
    paymentId: String,
) : RuntimeException("Payment with id $paymentId not found")
