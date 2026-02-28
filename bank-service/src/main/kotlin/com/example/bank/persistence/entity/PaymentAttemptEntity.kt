package com.example.bank.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "payment_attempts")
class PaymentAttemptEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "amount", nullable = false)
    var amount: Int,

    @Column(name = "payment_id", unique = true)
    var paymentId: String? = null,

    @Column(name = "card_number_last4", nullable = false)
    var cardNumberLast4: String,

    @Column(name = "status", nullable = false)
    var status: String,

    @Column(name = "reason")
    var reason: String?,

    @Column(name = "three_ds_code")
    var threeDsCode: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
