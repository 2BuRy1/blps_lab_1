package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("compensateDeclinedDelegate")
class CompensateDeclinedDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        ticketProcessService.compensateDeclinedOrderInternal(
            BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID),
        )
    }
}
