package com.example.bank.service

import com.example.bank.api.BankPayRequest
import com.example.bank.api.BankPayResponse
import com.example.bank.api.Confirm3dsRequest
import com.example.bank.persistence.entity.PaymentAttemptEntity
import com.example.bank.persistence.repository.PaymentAttemptRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class BankPaymentService(
    private val paymentAttemptRepository: PaymentAttemptRepository,
) {

    private val expiryFormatter = DateTimeFormatter.ofPattern("MM/yy")

    @Transactional
    fun pay(request: BankPayRequest): BankPayResponse {
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

        return response
    }

    @Transactional
    fun confirm3ds(paymentId: String, request: Confirm3dsRequest): BankPayResponse {
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

        return response
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
