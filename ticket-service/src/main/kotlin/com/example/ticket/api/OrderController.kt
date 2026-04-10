package com.example.ticket.api

import com.example.ticket.persistence.entity.OrderEntity
import com.example.ticket.persistence.repository.OrderRepository
import com.example.ticket.service.TicketProcessService
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class OrderController(
    private val ticketProcessService: TicketProcessService,
    private val orderRepository: OrderRepository,
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
    ): ResponseEntity<Any> {
        val response = ticketProcessService.payOrder(orderId, request)
        return when (response) {
            is PayOrderSuccessResponse -> ResponseEntity.ok(response)
            is PayOrderPending3dsResponse -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/orders/{orderId}/pay/confirm-3ds")
    fun confirm3ds(
        @PathVariable orderId: String,
        @RequestBody request: Confirm3dsRequest,
    ): ResponseEntity<PayOrderSuccessResponse> {
        return ResponseEntity.ok(ticketProcessService.confirm3ds(orderId, request))
    }

    @GetMapping("/orders/{orderId}/state")
    fun getState(@PathVariable orderId: String): ResponseEntity<OrderEntity> {
        val order = orderRepository.findByOrderIdForUpdate(orderId)
        return ResponseEntity.ok(orderRepository.findByOrderIdForUpdate(orderId))
    }
}
