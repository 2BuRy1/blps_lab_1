package com.example.bank.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BankPayRequest(
    val amount: Int,
    @JsonProperty("card_number") val cardNumber: String,
    @JsonProperty("expiration_date") val expirationDate: String,
    val cvv: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BankPayResponse(
    val status: Status,
    val reason: String? = null,
    @JsonProperty("payment_id") val paymentId: String? = null,
    @JsonProperty("challenge_message") val challengeMessage: String? = null,
) {
    enum class Status {
        SUCCESS,
        DECLINED,
        REQUIRES_3DS,
    }
}

data class Confirm3dsRequest(
    val code: String,
)

data class BankErrorResponse(
    val error: String,
    val message: String,
)
