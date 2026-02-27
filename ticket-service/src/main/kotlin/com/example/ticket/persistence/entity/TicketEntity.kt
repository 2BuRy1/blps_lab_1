package com.example.ticket.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "tickets")
class TicketEntity(
    @Id
    @Column(name = "ticket_id", nullable = false)
    var ticketId: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    var route: RouteEntity,

    @Column(name = "seat", nullable = false)
    var seat: String,

    @Column(name = "passport_id", nullable = false)
    var passportId: String,

    @Column(name = "full_name", nullable = false)
    var fullName: String,
)
