package com.example.ticket.persistence.entity

enum class OrderStatus {
    CREATED,
    PAYMENT_PROCESSING,
    PENDING_3DS,
    CONFIRMING_3DS,
    PAID,
    DECLINED,
    CANCELLED,
}
