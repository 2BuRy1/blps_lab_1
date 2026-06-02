package com.example.ticket.bpm

import com.example.ticket.api.AsyncOrderOperationAcceptedResponse
import com.example.ticket.api.CancelOrderResponse
import com.example.ticket.api.Confirm3dsRequest
import com.example.ticket.api.CreateOrderRequest
import com.example.ticket.api.OrderCreatedResponse
import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.client.BankPayDecision
import com.example.ticket.exception.ValidationException
import com.example.ticket.exception.WorkflowCorrelationException
import com.example.ticket.service.TicketProcessService
import org.camunda.bpm.engine.MismatchingMessageCorrelationException
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.TaskService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service

@Service
class CamundaOrderWorkflowService(
    private val ticketProcessService: TicketProcessService,
    private val runtimeService: RuntimeService,
    private val taskService: TaskService,
) {
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    fun startOrderProcess(request: CreateOrderRequest): OrderCreatedResponse {
        ticketProcessService.validateCreateOrderRequestForWorkflow(request)

        val instance = runtimeService.startProcessInstanceByKey(
            CamundaOrderProcess.PROCESS_DEFINITION_KEY,
            mapOf(
                CamundaOrderProcess.VAR_ROUTE_ID to request.routeId,
                CamundaOrderProcess.VAR_SEAT to request.seat,
                CamundaOrderProcess.VAR_PASSENGER_PASSPORT_ID to request.passenger.passportId,
                CamundaOrderProcess.VAR_PASSENGER_FULL_NAME to request.passenger.fullName,
            ),
        )

        val orderId = runtimeService.getVariable(instance.id, CamundaOrderProcess.VAR_ORDER_ID) as? String
            ?: throw IllegalStateException("Camunda process did not produce orderId")
        val amount = runtimeService.getVariable(instance.id, CamundaOrderProcess.VAR_AMOUNT) as? Int
            ?: throw IllegalStateException("Camunda process did not produce amount")

        return OrderCreatedResponse(orderId = orderId, status = OrderCreatedResponse.Status.CREATED, amount = amount)
    }

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    fun requestPayment(orderId: String, request: PayOrderRequest): AsyncOrderOperationAcceptedResponse {
        validateOrderId(orderId)
        ticketProcessService.validatePayOrderRequestForWorkflow(request)

        correlate(CamundaOrderProcess.MESSAGE_PAY_REQUESTED, orderId, mapOf(
            CamundaOrderProcess.VAR_CARD_NUMBER to request.cardNumber,
            CamundaOrderProcess.VAR_EXPIRATION_DATE to request.expirationDate,
            CamundaOrderProcess.VAR_CVV to request.cvv,
            CamundaOrderProcess.VAR_RETRY_MODE to false,
        ))
        return AsyncOrderOperationAcceptedResponse(orderId = orderId, message = "Payment request accepted. Poll /orders/$orderId/state for result.")
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun retryPayment(orderId: String, request: PayOrderRequest): AsyncOrderOperationAcceptedResponse {
        return retryPaymentInternal(orderId, request)
    }

    fun retryPaymentInternal(orderId: String, request: PayOrderRequest): AsyncOrderOperationAcceptedResponse {
        validateOrderId(orderId)
        ticketProcessService.validatePayOrderRequestForWorkflow(request)

        correlate(CamundaOrderProcess.MESSAGE_PAY_REQUESTED, orderId, mapOf(
            CamundaOrderProcess.VAR_CARD_NUMBER to request.cardNumber,
            CamundaOrderProcess.VAR_EXPIRATION_DATE to request.expirationDate,
            CamundaOrderProcess.VAR_CVV to request.cvv,
            CamundaOrderProcess.VAR_RETRY_MODE to true,
        ))
        return AsyncOrderOperationAcceptedResponse(orderId = orderId, message = "Retry payment request accepted. Poll /orders/$orderId/state for result.")
    }

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    fun request3dsConfirmation(orderId: String, request: Confirm3dsRequest): AsyncOrderOperationAcceptedResponse {
        validateOrderId(orderId)
        ticketProcessService.validateConfirm3dsRequestForWorkflow(request)

        complete3dsConfirmationForCurrentStep(orderId, request.code)
        return AsyncOrderOperationAcceptedResponse(orderId = orderId, message = "3DS confirmation accepted. Poll /orders/$orderId/state for result.")
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun requestCancel(orderId: String, event: String = "ORDER_CANCELLED"): CancelOrderResponse {
        return requestCancelInternal(orderId, event)
    }

    fun requestCancelInternal(orderId: String, event: String = "ORDER_CANCELLED"): CancelOrderResponse {
        validateOrderId(orderId)

        correlate(CamundaOrderProcess.MESSAGE_CANCEL_REQUESTED, orderId, mapOf(CamundaOrderProcess.VAR_CANCEL_EVENT to event))
        return CancelOrderResponse(orderId = orderId, status = CancelOrderResponse.Status.CANCELLED)
    }

    fun correlateBankDecision(bankResult: BankPayDecision) {
        val message = when (bankResult.status) {
            BankPayDecision.Status.SUCCESS -> CamundaOrderProcess.MESSAGE_BANK_SUCCESS
            BankPayDecision.Status.REQUIRES_3DS -> CamundaOrderProcess.MESSAGE_BANK_3DS_REQUIRED
            BankPayDecision.Status.DECLINED -> CamundaOrderProcess.MESSAGE_BANK_DECLINED
        }
        val vars = mutableMapOf<String, Any>()
        vars[CamundaOrderProcess.VAR_BANK_STATUS] = bankResult.status.name
        bankResult.paymentId?.let { vars[CamundaOrderProcess.VAR_BANK_PAYMENT_ID] = it }
        correlate(message, bankResult.orderId, vars)
    }

    fun complete3dsConfirmationTask(orderId: String, code: String) {
        validateOrderId(orderId)
        ticketProcessService.validateConfirm3dsRequestForWorkflow(Confirm3dsRequest(code))

        completeTaskByDefinition(
            processDefinitionKey = CamundaOrderProcess.PROCESS_DEFINITION_KEY,
            taskDefinitionKey = CamundaOrderProcess.TASK_DEF_SERVER_CONFIRM_3DS,
            orderId = orderId,
            code = code,
        ) ?: throw WorkflowCorrelationException("No active server 3DS confirmation task for orderId=$orderId")
    }

    fun complete3dsConfirmationForCurrentStep(orderId: String, code: String) {
        validateOrderId(orderId)
        ticketProcessService.validateConfirm3dsRequestForWorkflow(Confirm3dsRequest(code))

        val clientTaskCompleted = completeTaskByDefinition(
            processDefinitionKey = CamundaOrderProcess.PROCESS_CLIENT_KEY,
            taskDefinitionKey = CamundaOrderProcess.TASK_DEF_CLIENT_CONFIRM_3DS,
            orderId = orderId,
            code = code,
        )
        if (clientTaskCompleted != null) {
            return
        }

        val serverTaskCompleted = completeTaskByDefinition(
            processDefinitionKey = CamundaOrderProcess.PROCESS_DEFINITION_KEY,
            taskDefinitionKey = CamundaOrderProcess.TASK_DEF_SERVER_CONFIRM_3DS,
            orderId = orderId,
            code = code,
        )
        if (serverTaskCompleted != null) {
            return
        }

        throw WorkflowCorrelationException("No active 3DS confirmation step for orderId=$orderId")
    }

    private fun correlate(message: String, orderId: String, vars: Map<String, Any>) {
        try {
            val builder = runtimeService.createMessageCorrelation(message)
                .processInstanceVariableEquals(CamundaOrderProcess.VAR_ORDER_ID, orderId)
            vars.forEach { (k, v) -> builder.setVariable(k, v) }
            builder.correlateWithResult()
        } catch (ex: MismatchingMessageCorrelationException) {
            throw WorkflowCorrelationException("No active execution for message '$message' and orderId=$orderId", ex)
        }
    }

    private fun validateOrderId(orderId: String) {
        if (!orderId.matches(ORDER_ID_REGEX)) {
            throw ValidationException(details = listOf(ValidationDetail("orderId", "must match format o + 12 lowercase hex chars")))
        }
    }

    companion object {
        private val ORDER_ID_REGEX = Regex("^o[0-9a-f]{12}$")
    }

    private fun completeTaskByDefinition(
        processDefinitionKey: String,
        taskDefinitionKey: String,
        orderId: String,
        code: String,
    ): String? {
        val tasks = taskService.createTaskQuery()
            .processDefinitionKey(processDefinitionKey)
            .taskDefinitionKey(taskDefinitionKey)
            .processVariableValueEquals(CamundaOrderProcess.VAR_ORDER_ID, orderId)
            .active()
            .list()

        val task = tasks.singleOrNull() ?: return null
        taskService.complete(task.id, mapOf(CamundaOrderProcess.VAR_3DS_CODE to code))
        return task.id
    }
}
