package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("markPending3dsDelegate")
class MarkPending3dsDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        val paymentId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_BANK_PAYMENT_ID)
        log.info(
            "Camunda mark-pending-3ds started: processInstanceId={}, orderId={}, paymentId={}",
            execution.processInstanceId,
            orderId,
            paymentId,
        )
        ticketProcessService.markOrderPending3dsInternal(
            orderId,
            paymentId,
        )
        log.info(
            "Camunda mark-pending-3ds finished: processInstanceId={}, orderId={}, paymentId={}",
            execution.processInstanceId,
            orderId,
            paymentId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(MarkPending3dsDelegate::class.java)
    }
}
