package com.example.ticket.exception

import com.example.ticket.api.ConflictError
import com.example.ticket.api.NotFoundError
import com.example.ticket.api.ValidationDetail

class ValidationException(
    val details: List<ValidationDetail>,
    message: String = "Invalid request.",
) : RuntimeException(message)

class NotFoundException(
    val resource: NotFoundError.Resource,
    message: String,
) : RuntimeException(message)

class ConflictException(
    val code: ConflictError.Code,
    message: String,
) : RuntimeException(message)

class PaymentDeclinedException(
    message: String,
) : RuntimeException(message)
