package com.example.bank.service

import com.example.bank.api.BankPayRequest
import com.example.bank.api.BankPayResponse
import com.example.bank.api.Confirm3dsRequest
import com.example.bank.persistence.entity.PaymentAttemptEntity
import com.example.bank.persistence.repository.PaymentAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class BankPaymentService(
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val expiryFormatter = DateTimeFormatter.ofPattern("MM/yy")
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun pay(request: BankPayRequest): BankPayResponse {
        var txOutcome = "UNKNOWN"
        log.info("BANK_TX_BEGIN phase=bank op=pay amount={}", request.amount)

        return try {
            transactionTemplate.execute { _ ->
                validate(request)

                val response = when {
                    request.amount > 500_000 -> BankPayResponse(
                        status = BankPayResponse.Status.DECLINED,
                        reason = "INSUFFICIENT_FUNDS",
                    )

                    request.cvv == "000" -> BankPayResponse(
                        status = BankPayResponse.Status.DECLINED,
                        reason = "SUSPECTED_FRAUD",
                    )

                    request.cardNumber.endsWith("0000") -> BankPayResponse(
                        status = BankPayResponse.Status.DECLINED,
                        reason = "DECLINED_BY_ISSUER",
                    )

                    request.cardNumber.endsWith("1111") -> {
                        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                        BankPayResponse(
                            status = BankPayResponse.Status.REQUIRES_3DS,
                            reason = "3DS_REQUIRED",
                            paymentId = paymentId,
                            challengeMessage = "Confirm 3DS with code 123456",
                        )
                    }

                    else -> BankPayResponse(status = BankPayResponse.Status.SUCCESS)
                }

                paymentAttemptRepository.save(
                    PaymentAttemptEntity(
                        amount = request.amount,
                        paymentId = response.paymentId,
                        cardNumberLast4 = request.cardNumber.takeLast(4),
                        status = response.status.name,
                        reason = response.reason,
                        threeDsCode = if (response.status == BankPayResponse.Status.REQUIRES_3DS) "123456" else null,
                    )
                )

                txOutcome = response.status.name
                response
            } ?: throw IllegalStateException("Transaction returned null")
        } catch (ex: Exception) {
            txOutcome = "ERROR_${ex.javaClass.simpleName}"
            throw ex
        } finally {
            log.info("BANK_TX_END phase=bank op=pay outcome={}", txOutcome)
        }
    }

    fun confirm3ds(paymentId: String, request: Confirm3dsRequest): BankPayResponse {
        var txOutcome = "UNKNOWN"
        log.info("BANK_TX_BEGIN phase=bank op=confirm_3ds paymentId={}", paymentId)

        return try {
            transactionTemplate.execute { _ ->
                require(request.code.matches(Regex("^\\d{6}$"))) { "code must be 6 digits" }

                val payment = paymentAttemptRepository.findByPaymentIdForUpdate(paymentId)
                    ?: throw PaymentNotFoundException(paymentId)

                require(payment.status == BankPayResponse.Status.REQUIRES_3DS.name) {
                    "payment is not waiting for 3DS confirmation"
                }

                val response = if (payment.threeDsCode == request.code) {
                    BankPayResponse(status = BankPayResponse.Status.SUCCESS)
                } else {
                    BankPayResponse(
                        status = BankPayResponse.Status.DECLINED,
                        reason = "3DS_FAILED",
                    )
                }

                payment.status = response.status.name
                payment.reason = response.reason
                paymentAttemptRepository.save(payment)

                txOutcome = response.status.name
                response
            } ?: throw IllegalStateException("Transaction returned null")
        } catch (ex: Exception) {
            txOutcome = "ERROR_${ex.javaClass.simpleName}"
            throw ex
        } finally {
            log.info("BANK_TX_END phase=bank op=confirm_3ds paymentId={} outcome={}", paymentId, txOutcome)
        }
    }

    private fun validate(request: BankPayRequest) {
        require(request.amount >= 0) { "amount must be >= 0" }
        require(request.cardNumber.matches(Regex("^\\d{16}$"))) { "card_number must be 16 digits" }
        require(request.cvv.matches(Regex("^\\d{3}$"))) { "cvv must be 3 digits" }

        val expiry = try {
            YearMonth.parse(request.expirationDate, expiryFormatter)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("expiration_date must be in MM/YY format")
        }

        require(!expiry.isBefore(YearMonth.now())) { "card is expired" }
    }
}