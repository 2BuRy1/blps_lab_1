package com.example.ticket.api

import com.example.ticket.service.TicketProcessService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val ticketProcessService: TicketProcessService,
) {

    @PostMapping("/orders")
    fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<OrderCreatedResponse> {
        val response = ticketProcessService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/orders/{orderId}/pay")
    fun payOrder(
        @PathVariable orderId: String,
        @RequestBody request: PayOrderRequest,
    ): ResponseEntity<PayOrderSuccessResponse> {
        return ResponseEntity.ok(ticketProcessService.payOrder(orderId, request))
    }
}
