package com.example.ticket.client

import com.fasterxml.jackson.annotation.JsonProperty

data class BankPayRequestPayload(
    val amount: Int,
    @JsonProperty("card_number") val cardNumber: String,
    @JsonProperty("expiration_date") val expirationDate: String,
    val cvv: String,
)

data class BankPayDecision(
    val status: Status,
    val reason: String? = null,
) {
    enum class Status {
        SUCCESS,
        DECLINED,
    }
}

data class BankErrorPayload(
    val error: String,
    val message: String,
)
