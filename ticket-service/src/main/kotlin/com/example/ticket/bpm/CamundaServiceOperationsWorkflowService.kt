package com.example.ticket.bpm

import com.example.ticket.api.AsyncOrderOperationAcceptedResponse
import com.example.ticket.api.CancelOrderResponse
import com.example.ticket.api.ManagedRouteResponse
import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.UpdateRouteManageRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ValidationException
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.RuntimeService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class CamundaServiceOperationsWorkflowService(
    private val runtimeService: RuntimeService,
    private val ticketProcessService: TicketProcessService,
) {
    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun cancelOrder(orderId: String): CancelOrderResponse {
        validateOrderId(orderId)

        runtimeService.startProcessInstanceByKey(
            CamundaOrderProcess.PROCESS_SERVICE_OPERATIONS_KEY,
            mapOf(
                CamundaOrderProcess.VAR_REQUEST_TYPE to CamundaOrderProcess.REQUEST_TYPE_CANCEL_ORDER,
                CamundaOrderProcess.VAR_ORDER_ID to orderId,
            ),
        )
        return CancelOrderResponse(orderId = orderId, status = CancelOrderResponse.Status.CANCELLED)
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun retryPayment(orderId: String, request: PayOrderRequest): AsyncOrderOperationAcceptedResponse {
        validateOrderId(orderId)
        ticketProcessService.validatePayOrderRequestForWorkflow(request)

        runtimeService.startProcessInstanceByKey(
            CamundaOrderProcess.PROCESS_SERVICE_OPERATIONS_KEY,
            mapOf(
                CamundaOrderProcess.VAR_REQUEST_TYPE to CamundaOrderProcess.REQUEST_TYPE_RETRY_PAYMENT,
                CamundaOrderProcess.VAR_ORDER_ID to orderId,
                CamundaOrderProcess.VAR_CARD_NUMBER to request.cardNumber,
                CamundaOrderProcess.VAR_EXPIRATION_DATE to request.expirationDate,
                CamundaOrderProcess.VAR_CVV to request.cvv,
            ),
        )
        return AsyncOrderOperationAcceptedResponse(
            orderId = orderId,
            message = "Retry payment request accepted. Poll /orders/$orderId/state for result.",
        )
    }

    @PreAuthorize("hasAuthority('ROUTE_MANAGE')")
    fun updateRoute(routeId: String, request: UpdateRouteManageRequest): ManagedRouteResponse {
        runtimeService.startProcessInstanceByKey(
            CamundaOrderProcess.PROCESS_ROUTE_MANAGE_KEY,
            mapOf(
                "routeId" to routeId,
                "from" to request.from,
                "to" to request.to,
                "date" to request.date,
                "fromTerminal" to request.fromTerminal,
                "toTerminal" to request.toTerminal,
                "trainId" to request.trainId,
                "departureTime" to request.departureTime,
                "price" to request.price,
                "capacity" to request.capacity,
                "freeSeats" to request.freeSeats,
            ).filterValues { it != null },
        )
        return ticketProcessService.getRouteForManageInternal(routeId)
    }

    private fun validateOrderId(orderId: String) {
        if (!orderId.matches(ORDER_ID_REGEX)) {
            throw ValidationException(details = listOf(ValidationDetail("orderId", "must match format o + 12 lowercase hex chars")))
        }
    }

    companion object {
        private val ORDER_ID_REGEX = Regex("^o[0-9a-f]{12}$")
    }
}
