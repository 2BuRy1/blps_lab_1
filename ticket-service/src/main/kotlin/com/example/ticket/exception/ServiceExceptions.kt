package com.example.ticket.exception

import com.example.ticket.api.ConflictError
import com.example.ticket.api.IntegrationUnavailableError
import com.example.ticket.api.NotFoundError
import com.example.ticket.api.ValidationDetail
import java.io.Serializable

class ValidationException(
    val details: List<ValidationDetail>,
    message: String = "Invalid request.",
) : RuntimeException(message), Serializable

class NotFoundException(
    val resource: NotFoundError.Resource,
    message: String,
) : RuntimeException(message)

class ConflictException(
    val code: ConflictError.Code,
    message: String,
) : RuntimeException(message)

abstract class CommittedPaymentFailureException(
    message: String,
) : RuntimeException(message)

class PaymentDeclinedException(
    message: String,
) : CommittedPaymentFailureException(message)

class BankUnavailableAfterCompensationException(
    message: String,
) : CommittedPaymentFailureException(message)

class IntegrationUnavailableException(
    val service: IntegrationUnavailableError.Service = IntegrationUnavailableError.Service.BANK,
    message: String,
) : RuntimeException(message)

class DataBaseUnavailableException(
    message: String
): RuntimeException(message)
