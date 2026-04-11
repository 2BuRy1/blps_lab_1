package com.example.ticket.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.Size

@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @Column(name = "order_id", nullable = false)
    var orderId: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    var route: RouteEntity,

    @Column(name = "seat", nullable = false)
    var seat: String,

    @Column(name = "passport_id", nullable = false)
    var passportId: String,

    @Column(name = "full_name", nullable = false)
    @Size(max = 255)
    var fullName: String,

    @Column(name = "amount", nullable = false)
    var amount: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus,

    @Column(name = "bank_payment_id")
    var bankPaymentId: String? = null,

    @Column(name = "ticket_id")
    var ticketId: String? = null,

    @Column(name = "crm_deal_id")
    var crmDealId: Long? = null,
)
