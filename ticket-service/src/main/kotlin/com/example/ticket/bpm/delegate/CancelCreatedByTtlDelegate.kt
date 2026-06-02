package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("cancelCreatedByTtlDelegate")
class CancelCreatedByTtlDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        ticketProcessService.cancelOrderInternal(
            BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID),
            "ORDER_CANCELLED_BY_CREATED_TTL",
        )
    }
}
