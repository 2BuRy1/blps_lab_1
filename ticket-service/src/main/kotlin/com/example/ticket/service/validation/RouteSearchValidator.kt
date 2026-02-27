package com.example.ticket.service.validation

import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ValidationException
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

@Component
class RouteSearchValidator {

    fun validateAndBuild(
        from: String,
        to: String,
        date: String,
        fromTerminal: String?,
        toTerminal: String?,
        minPrice: Int?,
        maxPrice: Int?,
        departureAfter: String?,
        departureBefore: String?,
        onlyWithSeats: Boolean?,
        sort: String?,
    ): RouteSearchCriteria {
        val details = mutableListOf<ValidationDetail>()

        if (from.isBlank()) details += ValidationDetail("from", "must not be blank")
        if (to.isBlank()) details += ValidationDetail("to", "must not be blank")

        val travelDate = parseDate(date, details)
        val afterTime = parseTime("departure_after", departureAfter, details)
        val beforeTime = parseTime("departure_before", departureBefore, details)

        if (minPrice != null && minPrice < 0) details += ValidationDetail("min_price", "must be >= 0")
        if (maxPrice != null && maxPrice < 0) details += ValidationDetail("max_price", "must be >= 0")
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            details += ValidationDetail("max_price", "must be >= min_price")
        }
        if (afterTime != null && beforeTime != null && afterTime > beforeTime) {
            details += ValidationDetail("departure_before", "must be later than departure_after")
        }

        val sortMode = try {
            RouteSortMode.fromValue(sort)
        } catch (_: IllegalArgumentException) {
            details += ValidationDetail("sort", "unsupported sort value")
            RouteSortMode.PRICE_ASC
        }

        if (details.isNotEmpty() || travelDate == null) {
            throw ValidationException(details = details)
        }

        return RouteSearchCriteria(
            from = from,
            to = to,
            travelDate = travelDate,
            fromTerminal = fromTerminal,
            toTerminal = toTerminal,
            minPrice = minPrice,
            maxPrice = maxPrice,
            departureAfter = afterTime,
            departureBefore = beforeTime,
            onlyWithSeats = onlyWithSeats == true,
            sortMode = sortMode,
        )
    }

    private fun parseDate(rawDate: String, details: MutableList<ValidationDetail>): LocalDate? {
        return try {
            LocalDate.parse(rawDate)
        } catch (_: DateTimeParseException) {
            details += ValidationDetail("date", "must be in YYYY-MM-DD format")
            null
        }
    }

    private fun parseTime(
        field: String,
        value: String?,
        details: MutableList<ValidationDetail>,
    ): LocalTime? {
        if (value == null) return null
        return try {
            LocalTime.parse(value)
        } catch (_: DateTimeParseException) {
            details += ValidationDetail(field, "must be in HH:mm format")
            null
        }
    }
}
