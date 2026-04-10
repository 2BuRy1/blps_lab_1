package com.example.ticket.api

import com.example.ticket.service.TicketProcessService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val ticketProcessService: TicketProcessService,
) {

    @PostMapping("/orders")
    fun createOrder(@RequestBody @Valid request: CreateOrderRequest): ResponseEntity<OrderCreatedResponse> {
        val response = ticketProcessService.createOrder(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/orders/{orderId}/pay")
    fun payOrder(
        @PathVariable orderId: String,
        @RequestBody request: PayOrderRequest,
    ): ResponseEntity<AsyncOrderOperationAcceptedResponse> {
        return ResponseEntity.accepted().body(ticketProcessService.payOrder(orderId, request))
    }

    @PostMapping("/orders/{orderId}/pay/confirm-3ds")
    fun confirm3ds(
        @PathVariable orderId: String,
        @RequestBody request: Confirm3dsRequest,
    ): ResponseEntity<AsyncOrderOperationAcceptedResponse> {
        return ResponseEntity.accepted().body(ticketProcessService.confirm3ds(orderId, request))
    }

    @GetMapping("/orders/{orderId}/state")
    fun getState(@PathVariable orderId: String): ResponseEntity<OrderStateResponse> {
        return ResponseEntity.ok(ticketProcessService.getOrderState(orderId))
    }
}
