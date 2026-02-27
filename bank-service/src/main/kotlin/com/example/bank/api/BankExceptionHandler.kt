package com.example.bank.api

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
}
