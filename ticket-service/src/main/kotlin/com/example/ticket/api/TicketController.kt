package com.example.ticket.api

import com.example.ticket.service.TicketProcessService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class TicketController(
    private val ticketProcessService: TicketProcessService,
) {

    @GetMapping("/tickets/{ticketId}")
    fun getTicket(@PathVariable ticketId: String): Ticket {
        return ticketProcessService.getTicket(ticketId)
    }
}
