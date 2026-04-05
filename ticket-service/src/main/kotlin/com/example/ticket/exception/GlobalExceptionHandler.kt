package com.example.ticket.exception

import com.example.ticket.api.ConflictError
import com.example.ticket.api.IntegrationUnavailableError
import com.example.ticket.api.NotFoundError
import com.example.ticket.api.PaymentDeclinedError
import com.example.ticket.api.ValidationDetail
import com.example.ticket.api.ValidationError
import jakarta.persistence.PersistenceException
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.jpa.JpaSystemException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.sql.SQLException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ValidationError> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ValidationError(message = ex.message ?: "Invalid request.", details = ex.details))
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<NotFoundError> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(NotFoundError(resource = ex.resource, message = ex.message ?: "Resource not found."))
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ConflictError> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ConflictError(code = ex.code, message = ex.message ?: "Conflict."))
    }

    @ExceptionHandler(PaymentDeclinedException::class)
    fun handlePaymentDeclined(ex: PaymentDeclinedException): ResponseEntity<PaymentDeclinedError> {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
            .body(PaymentDeclinedError(message = ex.message ?: "Payment was declined by the bank."))
    }

    @ExceptionHandler(BankUnavailableAfterCompensationException::class)
    fun handleBankUnavailableAfterCompensation(ex: BankUnavailableAfterCompensationException): ResponseEntity<IntegrationUnavailableError> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                IntegrationUnavailableError(
                    message = ex.message ?: "Bank is unavailable. Order was declined and seat released.",
                )
            )
    }

    @ExceptionHandler(IntegrationUnavailableException::class)
    fun handleIntegrationUnavailable(ex: IntegrationUnavailableException): ResponseEntity<IntegrationUnavailableError> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                IntegrationUnavailableError(
                    message = ex.message ?: "Integration with external service is unavailable.",
                )
            )
    }

    @ExceptionHandler(DataBaseUnavailableException::class)
    fun handleDatabaseUnavailable(ex: DataBaseUnavailableException): ResponseEntity<IntegrationUnavailableError> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                IntegrationUnavailableError(
                    message = ex.message ?: "Database is unavailable. Process was terminated.",
                )
            )
    }

    @ExceptionHandler(
        DataAccessException::class,
        CannotCreateTransactionException::class,
        JpaSystemException::class,
        PersistenceException::class,
        SQLException::class,
    )
    fun handlePersistenceUnavailable(ex: Exception): ResponseEntity<IntegrationUnavailableError> {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                IntegrationUnavailableError(
                    message = "Database is unavailable. Process was terminated.",
                )
            )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(ex: IllegalArgumentException): ResponseEntity<ValidationError> {
        return validationResponse("request", ex.message ?: "Invalid request")
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ValidationError> {
        return validationResponse(ex.name, "invalid value")
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<ValidationError> {
        return validationResponse(ex.parameterName, "is required")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadJson(): ResponseEntity<ValidationError> {
        return validationResponse("body", "malformed JSON")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ValidationError> {
        val details = ex.bindingResult.fieldErrors
            .map { ValidationDetail(it.field, it.defaultMessage ?: "invalid") }
            .ifEmpty { listOf(ValidationDetail("request", "invalid")) }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ValidationError(message = "Invalid request.", details = details))
    }

    private fun validationResponse(field: String, issue: String): ResponseEntity<ValidationError> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ValidationError(
                    message = "Invalid request.",
                    details = listOf(ValidationDetail(field = field, issue = issue)),
                )
            )
    }
}
