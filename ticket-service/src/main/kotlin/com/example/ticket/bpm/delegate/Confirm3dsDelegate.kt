package com.example.ticket.bpm.delegate

import com.example.ticket.api.Confirm3dsRequest
import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("confirm3dsDelegate")
class Confirm3dsDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        val code = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_3DS_CODE)
        ticketProcessService.validateConfirm3dsRequestForWorkflow(Confirm3dsRequest(code))
        log.info(
            "Camunda 3DS confirmation started: processInstanceId={}, orderId={}",
            execution.processInstanceId,
            orderId,
        )
        ticketProcessService.confirm3dsInternal(orderId, code)
        log.info(
            "Camunda 3DS confirmation dispatched: processInstanceId={}, orderId={}",
            execution.processInstanceId,
            orderId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(Confirm3dsDelegate::class.java)
    }
}
