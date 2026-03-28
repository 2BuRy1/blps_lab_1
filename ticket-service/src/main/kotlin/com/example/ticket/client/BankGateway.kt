package com.example.ticket.client

import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.IntegrationUnavailableException
import com.example.ticket.exception.ValidationException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class BankGateway(
    private val bankRestClient: RestClient,
    private val objectMapper: ObjectMapper,
    @Value("\${integration.bank.username}") bankUsername: String,
    @Value("\${integration.bank.password}") bankPassword: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val bankUsername = bankUsername.trim()
    private val bankPassword = bankPassword.trim()
    private val authFingerprint = fingerprint("$bankUsername:$bankPassword")

    init {
        require(bankUsername.isNotBlank()) { "bank username must not be blank" }
        require(bankPassword.isNotBlank()) { "bank password must not be blank" }
    }

    fun authorize(amount: Int, request: PayOrderRequest): BankPayDecision {
        val payload = BankPayRequestPayload(
            amount = amount,
            cardNumber = request.cardNumber,
            expirationDate = request.expirationDate,
            cvv = request.cvv,
        )
        var txOutcome = "UNKNOWN"
        log.info("BANK_TX_BEGIN phase=ticket_to_bank op=pay amount={}", amount)
        log.info("BANK_OUT authorize username={} amount={}", bankUsername, amount)
        log.info("BANK_OUT auth_fingerprint={}", authFingerprint)

        return try {
            val decision = bankRestClient.post()
                .uri("/bank/pay")
                .headers { headers -> applyBankAuth(headers) }
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(BankPayDecision::class.java)
                ?: throw IllegalStateException("Empty bank response")
            txOutcome = decision.status.name
            decision
        } catch (ex: RestClientResponseException) {
            txOutcome = "HTTP_${ex.statusCode.value()}"
            if (ex.statusCode.value() == 400) {
                throw ValidationException(
                    details = listOf(
                        ValidationDetail(
                            field = "payment",
                            issue = parseBankErrorMessage(ex.responseBodyAsString),
                        )
                    )
                )
            }
            throw IntegrationUnavailableException(
                "Bank integration failed with HTTP ${ex.statusCode.value()}: ${ex.responseBodyAsString.take(200)}",
            )
        } catch (ex: Exception) {
            txOutcome = "ERROR_${ex.javaClass.simpleName}"
            throw IntegrationUnavailableException(
                "Bank integration is unavailable: ${ex.javaClass.simpleName}.",
            )
        } finally {
            log.info("BANK_TX_END phase=ticket_to_bank op=pay outcome={}", txOutcome)
        }
    }

    fun confirm3ds(paymentId: String, code: String): BankPayDecision {
        val payload = BankConfirm3dsPayload(code = code)
        var txOutcome = "UNKNOWN"
        log.info("BANK_TX_BEGIN phase=ticket_to_bank op=confirm_3ds paymentId={}", paymentId)
        log.info("BANK_OUT confirm3ds username={} paymentId={}", bankUsername, paymentId)
        log.info("BANK_OUT auth_fingerprint={}", authFingerprint)

        return try {
            val decision = bankRestClient.post()
                .uri("/bank/pay/{paymentId}/confirm-3ds", paymentId)
                .headers { headers -> applyBankAuth(headers) }
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(BankPayDecision::class.java)
                ?: throw IllegalStateException("Empty bank response")
            txOutcome = decision.status.name
            decision
        } catch (ex: RestClientResponseException) {
            txOutcome = "HTTP_${ex.statusCode.value()}"
            if (ex.statusCode.value() == 400) {
                throw ValidationException(
                    details = listOf(
                        ValidationDetail(
                            field = "code",
                            issue = parseBankErrorMessage(ex.responseBodyAsString),
                        )
                    )
                )
            }
            throw IntegrationUnavailableException(
                "Bank integration failed with HTTP ${ex.statusCode.value()}: ${ex.responseBodyAsString.take(200)}",
            )
        } catch (ex: Exception) {
            txOutcome = "ERROR_${ex.javaClass.simpleName}"
            throw IntegrationUnavailableException(
                "Bank integration is unavailable: ${ex.javaClass.simpleName}.",
            )
        } finally {
            log.info("BANK_TX_END phase=ticket_to_bank op=confirm_3ds paymentId={} outcome={}", paymentId, txOutcome)
        }
    }

    private fun applyBankAuth(headers: HttpHeaders) {
        headers.setBasicAuth(bankUsername, bankPassword)
        headers.accept = listOf(MediaType.APPLICATION_JSON)
    }

    private fun parseBankErrorMessage(body: String): String {
        return try {
            objectMapper.readValue(body, BankErrorPayload::class.java).message
        } catch (_: Exception) {
            "Invalid payment request"
        }
    }

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}
