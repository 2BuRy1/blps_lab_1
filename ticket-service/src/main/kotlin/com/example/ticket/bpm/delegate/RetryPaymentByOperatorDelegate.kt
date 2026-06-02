package com.example.ticket.bpm.delegate

import com.example.ticket.api.PayOrderRequest
import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.bpm.CamundaOrderWorkflowService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("retryPaymentByOperatorDelegate")
class RetryPaymentByOperatorDelegate(
    private val camundaOrderWorkflowService: CamundaOrderWorkflowService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        val request = PayOrderRequest(
            cardNumber = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_CARD_NUMBER),
            expirationDate = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_EXPIRATION_DATE),
            cvv = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_CVV),
        )
        camundaOrderWorkflowService.retryPaymentInternal(orderId, request)
    }
}
