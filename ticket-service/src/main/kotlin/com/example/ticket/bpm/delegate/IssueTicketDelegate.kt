package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("issueTicketDelegate")
class IssueTicketDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        log.info("Camunda issue-ticket started: processInstanceId={}, orderId={}", execution.processInstanceId, orderId)
        val result = ticketProcessService.issueTicketInternal(orderId)
        execution.setVariable("ticketId", result.ticketId)
        log.info(
            "Camunda issue-ticket finished: processInstanceId={}, orderId={}, ticketId={}",
            execution.processInstanceId,
            orderId,
            result.ticketId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(IssueTicketDelegate::class.java)
    }
}
