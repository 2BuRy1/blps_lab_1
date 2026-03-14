package com.example.bank.service

class PaymentNotFoundException(
    paymentId: String,
) : RuntimeException("Payment with id $paymentId not found")
