package com.example.bank.api

import com.example.bank.service.PaymentNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class BankExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidation(ex: IllegalArgumentException): ResponseEntity<BankErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(BankErrorResponse(error = "INVALID_REQUEST", message = ex.message ?: "Invalid request"))
    }

    @ExceptionHandler(PaymentNotFoundException::class)
    fun handleNotFound(ex: PaymentNotFoundException): ResponseEntity<BankErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(BankErrorResponse(error = "PAYMENT_NOT_FOUND", message = ex.message ?: "Payment not found"))
    }
}
