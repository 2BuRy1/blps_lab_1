package com.example.ticket.api

import com.example.ticket.service.TicketProcessService
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class RouteController(
    private val ticketProcessService: TicketProcessService,
) {

    @GetMapping("/routes")
    fun searchRoutes(
        @RequestParam from: String,
        @RequestParam to: String,
        @RequestParam date: String,
        @RequestParam("from_terminal", required = false) fromTerminal: String?,
        @RequestParam("to_terminal", required = false) toTerminal: String?,
        @RequestParam("min_price", required = false) minPrice: Int?,
        @RequestParam("max_price", required = false) maxPrice: Int?,
        @RequestParam("departure_after", required = false) departureAfter: String?,
        @RequestParam("departure_before", required = false) departureBefore: String?,
        @RequestParam("only_with_seats", required = false) onlyWithSeats: Boolean?,
        @RequestParam(required = false) sort: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): RoutesResponse {
        return ticketProcessService.searchRoutes(
            from = from,
            to = to,
            date = date,
            fromTerminal = fromTerminal,
            toTerminal = toTerminal,
            minPrice = minPrice,
            maxPrice = maxPrice,
            departureAfter = departureAfter,
            departureBefore = departureBefore,
            onlyWithSeats = onlyWithSeats,
            sort = sort,
            pageable = pageable,
        )
    }
}
