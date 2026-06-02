package com.example.ticket.bpm.delegate

import com.example.ticket.api.PayOrderRequest
import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("startPaymentDelegate")
class StartPaymentDelegate(
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {
    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        val request = PayOrderRequest(
            cardNumber = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_CARD_NUMBER),
            expirationDate = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_EXPIRATION_DATE),
            cvv = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_CVV),
        )
        ticketProcessService.validatePayOrderRequestForWorkflow(request)
        val retryMode = BpmVariables.optionalBoolean(execution, CamundaOrderProcess.VAR_RETRY_MODE)
        log.info(
            "Camunda payment started: processInstanceId={}, orderId={}, retryMode={}",
            execution.processInstanceId,
            orderId,
            retryMode,
        )
        ticketProcessService.startPaymentForWorkflow(orderId, request, retryMode)
        log.info(
            "Camunda payment dispatched: processInstanceId={}, orderId={}, retryMode={}",
            execution.processInstanceId,
            orderId,
            retryMode,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(StartPaymentDelegate::class.java)
    }
}
