package com.example.ticket.api

import com.example.ticket.service.TicketProcessService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/service")
class ServiceOperationsController(
    private val ticketProcessService: TicketProcessService,
) {

    @GetMapping("/orders/{orderId}")
    fun getOrderForManage(@PathVariable orderId: String): ManagedOrderResponse {
        return ticketProcessService.getOrderForManage(orderId)
    }

    @PostMapping("/orders/{orderId}/cancel")
    fun cancelOrder(@PathVariable orderId: String): ResponseEntity<CancelOrderResponse> {
        return ResponseEntity.status(HttpStatus.OK)
            .body(ticketProcessService.cancelOrder(orderId))
    }

    @PostMapping("/orders/{orderId}/retry-payment")
    fun retryPayment(
        @PathVariable orderId: String,
        @RequestBody request: PayOrderRequest,
    ): ResponseEntity<Any> {
        val response = ticketProcessService.retryPayment(orderId, request)
        return when (response) {
            is PayOrderSuccessResponse -> ResponseEntity.ok(response)
            is PayOrderPending3dsResponse -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
            else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PatchMapping("/routes/{routeId}")
    fun updateRoute(
        @PathVariable routeId: String,
        @RequestBody request: UpdateRouteManageRequest,
    ): ManagedRouteResponse {
        return ticketProcessService.updateRouteForManage(routeId, request)
    }
}
