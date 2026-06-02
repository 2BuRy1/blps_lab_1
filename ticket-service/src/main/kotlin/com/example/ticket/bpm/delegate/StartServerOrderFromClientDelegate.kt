package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.api.CreateOrderRequest
import com.example.ticket.api.Passenger
import com.example.ticket.exception.ValidationException
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("startServerOrderFromClientDelegate")
class StartServerOrderFromClientDelegate(
    private val runtimeService: RuntimeService,
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val variables = try {
            BpmFormValidator.validateOptionalPassengerContacts(execution)
            val request = CreateOrderRequest(
                routeId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ROUTE_ID),
                seat = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_SEAT),
                passenger = Passenger(
                    passportId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_PASSENGER_PASSPORT_ID),
                    fullName = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_PASSENGER_FULL_NAME),
                ),
            )
            ticketProcessService.validateCreateOrderRequestForWorkflow(request)
            mapOf(
                CamundaOrderProcess.VAR_ROUTE_ID to request.routeId,
                CamundaOrderProcess.VAR_SEAT to request.seat,
                CamundaOrderProcess.VAR_PASSENGER_PASSPORT_ID to request.passenger.passportId,
                CamundaOrderProcess.VAR_PASSENGER_FULL_NAME to request.passenger.fullName,
            )
        } catch (ex: ValidationException) {
            BpmValidationErrors.throwBpmnError(execution, ex)
        }

        log.info(
            "Client process starting server order: clientProcessInstanceId={}, routeId={}, seat={}",
            execution.processInstanceId,
            variables[CamundaOrderProcess.VAR_ROUTE_ID],
            variables[CamundaOrderProcess.VAR_SEAT],
        )

        val serverInstance = runtimeService.startProcessInstanceByKey(
            CamundaOrderProcess.PROCESS_DEFINITION_KEY,
            variables,
        )

        val orderId = runtimeService.getVariable(serverInstance.id, CamundaOrderProcess.VAR_ORDER_ID) as? String
            ?: throw IllegalStateException("Server order process did not produce orderId")
        val amount = (runtimeService.getVariable(serverInstance.id, CamundaOrderProcess.VAR_AMOUNT) as? Number)?.toInt()
            ?: throw IllegalStateException("Server order process did not produce amount")

        execution.setVariable(CamundaOrderProcess.VAR_ORDER_ID, orderId)
        execution.setVariable(CamundaOrderProcess.VAR_AMOUNT, amount)
        execution.setVariable("serverProcessInstanceId", serverInstance.id)

        log.info(
            "Client process linked to server order: clientProcessInstanceId={}, serverProcessInstanceId={}, orderId={}, amount={}",
            execution.processInstanceId,
            serverInstance.id,
            orderId,
            amount,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(StartServerOrderFromClientDelegate::class.java)
    }
}
