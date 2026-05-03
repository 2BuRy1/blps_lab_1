package com.example.ticket.bpm.delegate

import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.exception.ConflictException
import com.example.ticket.exception.ValidationException
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.MismatchingMessageCorrelationException
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("requestServerPaymentFromClientDelegate")
class RequestServerPaymentFromClientDelegate(
    private val runtimeService: RuntimeService,
    private val ticketProcessService: TicketProcessService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val orderId: String
        val request: PayOrderRequest
        try {
            orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
            request = PayOrderRequest(
                cardNumber = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_CARD_NUMBER),
                expirationDate = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_EXPIRATION_DATE),
                cvv = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_CVV),
            )
            ticketProcessService.validatePayOrderRequestForWorkflow(request)
        } catch (ex: ValidationException) {
            BpmValidationErrors.throwBpmnError(execution, ex)
        }

        log.info(
            "Client process requesting payment in server process: clientProcessInstanceId={}, orderId={}",
            execution.processInstanceId,
            orderId,
        )

        try {
            runtimeService.createMessageCorrelation(CamundaOrderProcess.MESSAGE_PAY_REQUESTED)
                .processInstanceVariableEquals(CamundaOrderProcess.VAR_ORDER_ID, orderId)
                .setVariable(CamundaOrderProcess.VAR_CARD_NUMBER, request.cardNumber)
                .setVariable(CamundaOrderProcess.VAR_EXPIRATION_DATE, request.expirationDate)
                .setVariable(CamundaOrderProcess.VAR_CVV, request.cvv)
                .setVariable(CamundaOrderProcess.VAR_RETRY_MODE, false)
                .correlateWithResult()
        } catch (ex: MismatchingMessageCorrelationException) {
            BpmValidationErrors.throwBpmnError(
                execution,
                ValidationException(
                    details = listOf(ValidationDetail("orderId", "order is not waiting for payment")),
                ),
            )
        } catch (ex: ConflictException) {
            BpmValidationErrors.throwBpmnError(
                execution,
                ValidationException(
                    details = listOf(ValidationDetail("order", ex.message ?: "Order is not in payable state.")),
                ),
            )
        }

        log.info(
            "Client process payment request correlated: clientProcessInstanceId={}, orderId={}",
            execution.processInstanceId,
            orderId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(RequestServerPaymentFromClientDelegate::class.java)
    }
}
