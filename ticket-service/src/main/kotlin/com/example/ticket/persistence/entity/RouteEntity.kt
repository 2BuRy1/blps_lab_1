package com.example.ticket.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "routes")
class RouteEntity(
    @Id
    @Column(name = "route_id", nullable = false)
    var routeId: String,

    @Column(name = "from_city", nullable = false)
    var fromCity: String,

    @Column(name = "to_city", nullable = false)
    var toCity: String,

    @Column(name = "travel_date", nullable = false)
    var travelDate: LocalDate,

    @Column(name = "from_terminal")
    var fromTerminal: String?,

    @Column(name = "to_terminal")
    var toTerminal: String?,

    @Column(name = "train_id", nullable = false)
    var trainId: String,

    @Column(name = "departure_time", nullable = false)
    var departureTime: LocalTime,

    @Column(name = "price", nullable = false)
    var price: Int,

    @Column(name = "capacity", nullable = false)
    var capacity: Int,

    @Column(name = "reserved_seats", nullable = false)
    var reservedSeats: Int = 0,

    @Column(name = "free_seats", nullable = false, columnDefinition = "integer default 0")
    var freeSeats: Int = capacity,
)
