package com.example.ticket.service.validation

import org.springframework.data.domain.Sort
import java.time.LocalDate
import java.time.LocalTime

data class RouteSearchCriteria(
    val from: String,
    val to: String,
    val travelDate: LocalDate,
    val fromTerminal: String?,
    val toTerminal: String?,
    val minPrice: Int?,
    val maxPrice: Int?,
    val departureAfter: LocalTime?,
    val departureBefore: LocalTime?,
    val onlyWithSeats: Boolean,
    val sortMode: RouteSortMode,
)

enum class RouteSortMode(val value: String) {
    PRICE_ASC("price:asc"),
    PRICE_DESC("price:desc"),
    DEPARTURE_ASC("departureTime:asc"),
    DEPARTURE_DESC("departureTime:desc"),
    FREE_SEATS_ASC("freeSeats:asc"),
    FREE_SEATS_DESC("freeSeats:desc");

    fun toSort(): Sort {
        return when (this) {
            PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price")
            PRICE_DESC -> Sort.by(Sort.Direction.DESC, "price")
            DEPARTURE_ASC -> Sort.by(Sort.Direction.ASC, "departureTime")
            DEPARTURE_DESC -> Sort.by(Sort.Direction.DESC, "departureTime")
            FREE_SEATS_ASC -> Sort.by(Sort.Direction.ASC, "freeSeats")
            FREE_SEATS_DESC -> Sort.by(Sort.Direction.DESC, "freeSeats")
        }
    }

    companion object {
        fun fromValue(value: String?): RouteSortMode {
            if (value == null) return PRICE_ASC
            return entries.find { it.value == value } ?: throw IllegalArgumentException("Unsupported sort value")
        }
    }
}
