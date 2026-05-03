package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("cancelOrderDelegate")
class CancelOrderDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        ticketProcessService.cancelOrderInternal(
            orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID),
            event = BpmVariables.optionalString(execution, CamundaOrderProcess.VAR_CANCEL_EVENT) ?: "ORDER_CANCELLED",
        )
    }
}
