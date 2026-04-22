package com.example.ticket.client.bitrix

data class Bitrix24DealSyncPayload(
    val orderId: String,
    val routeId: String,
    val from: String,
    val to: String,
    val date: String,
    val seat: String,
    val amount: Int,
    val status: String,
    val passengerName: String,
    val passengerPassportId: String,
    val bankPaymentId: String?,
    val ticketId: String?,
    val event: String,
)

data class Bitrix24DealAddRequest(
    val fields: Map<String, Any>,
)

data class Bitrix24DealUpdateRequest(
    val id: Long,
    val fields: Map<String, Any>,
)
