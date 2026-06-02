package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.bpm.CamundaOrderWorkflowService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("cancelOrderByOperatorDelegate")
class CancelOrderByOperatorDelegate(
    private val camundaOrderWorkflowService: CamundaOrderWorkflowService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        camundaOrderWorkflowService.requestCancelInternal(orderId, "ORDER_CANCELLED_BY_OPERATOR")
    }
}
