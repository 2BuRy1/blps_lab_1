package com.example.ticket.bpm.delegate

import com.example.ticket.api.CreateOrderRequest
import com.example.ticket.api.Passenger
import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import com.fasterxml.jackson.databind.ObjectMapper
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component("createOrderDelegate")
class CreateOrderDelegate(
    private val ticketProcessService: TicketProcessService,
    @Value("\${jobs.order-expiration.created-ttl:PT15M}") private val createdTtl: Duration,
    @Value("\${jobs.order-expiration.pending-3ds-ttl:PT15M}") private val pending3dsTtl: Duration,
    private val objectMapper: ObjectMapper
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val request = CreateOrderRequest(
            routeId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ROUTE_ID),
            seat = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_SEAT),
            passenger = Passenger(
                passportId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_PASSENGER_PASSPORT_ID),
                fullName = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_PASSENGER_FULL_NAME),
            ),
        )
        BpmFormValidator.validateOptionalPassengerContacts(execution, objectMapper)
        ticketProcessService.validateCreateOrderRequestForWorkflow(request)
        log.info(
            "Camunda create-order started: processInstanceId={}, routeId={}, seat={}",
            execution.processInstanceId,
            request.routeId,
            request.seat,
        )
        val response = ticketProcessService.createOrderInternal(request)
        execution.setVariable(CamundaOrderProcess.VAR_ORDER_ID, response.orderId)
        execution.setVariable(CamundaOrderProcess.VAR_AMOUNT, response.amount)
        execution.setVariable(CamundaOrderProcess.VAR_CREATED_TTL, if (createdTtl.isNegative || createdTtl.isZero) "PT1S" else createdTtl.toString())
        execution.setVariable(CamundaOrderProcess.VAR_PENDING_3DS_TTL, if (pending3dsTtl.isNegative || pending3dsTtl.isZero) "PT1S" else pending3dsTtl.toString())
        log.info(
            "Camunda create-order finished: processInstanceId={}, orderId={}, amount={}",
            execution.processInstanceId,
            response.orderId,
            response.amount,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CreateOrderDelegate::class.java)
    }
}
