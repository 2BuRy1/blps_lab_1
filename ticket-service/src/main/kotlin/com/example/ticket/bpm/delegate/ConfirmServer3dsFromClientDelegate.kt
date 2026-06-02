package com.example.ticket.bpm.delegate

import com.example.ticket.api.ValidationDetail
import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.bpm.CamundaOrderWorkflowService
import com.example.ticket.exception.ValidationException
import com.example.ticket.exception.WorkflowCorrelationException
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("confirmServer3dsFromClientDelegate")
class ConfirmServer3dsFromClientDelegate(
    private val camundaOrderWorkflowService: CamundaOrderWorkflowService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val orderId: String
        val code: String
        try {
            orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
            code = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_3DS_CODE)
        } catch (ex: ValidationException) {
            BpmValidationErrors.throwBpmnError(execution, ex)
        }

        log.info(
            "Client process sending 3DS confirmation to server process: clientProcessInstanceId={}, orderId={}",
            execution.processInstanceId,
            orderId,
        )

        try {
            camundaOrderWorkflowService.complete3dsConfirmationTask(orderId, code)
        } catch (ex: ValidationException) {
            BpmValidationErrors.throwBpmnError(execution, ex)
        } catch (ex: WorkflowCorrelationException) {
            BpmValidationErrors.throwBpmnError(
                execution,
                ValidationException(
                    details = listOf(ValidationDetail("orderId", "order is not waiting for 3DS confirmation")),
                ),
            )
        }
        log.info("Client process completed server 3DS task: orderId={}", orderId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ConfirmServer3dsFromClientDelegate::class.java)
    }
}
