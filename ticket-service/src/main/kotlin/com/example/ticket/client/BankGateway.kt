package com.example.ticket.client

import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ValidationException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class BankGateway(
    private val bankRestClient: RestClient,
    private val objectMapper: ObjectMapper,
) {

    fun authorize(amount: Int, request: PayOrderRequest): BankPayDecision {
        val payload = BankPayRequestPayload(
            amount = amount,
            cardNumber = request.cardNumber,
            expirationDate = request.expirationDate,
            cvv = request.cvv,
        )

        return try {
            bankRestClient.post()
                .uri("/bank/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(BankPayDecision::class.java)
                ?: throw IllegalStateException("Empty bank response")
        } catch (ex: RestClientResponseException) {
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
            BankPayDecision(status = BankPayDecision.Status.DECLINED, reason = "BANK_UNAVAILABLE")
        } catch (_: Exception) {
            BankPayDecision(status = BankPayDecision.Status.DECLINED, reason = "BANK_UNAVAILABLE")
        }
    }

    private fun parseBankErrorMessage(body: String): String {
        return try {
            objectMapper.readValue(body, BankErrorPayload::class.java).message
        } catch (_: Exception) {
            "Invalid payment request"
        }
    }
}
