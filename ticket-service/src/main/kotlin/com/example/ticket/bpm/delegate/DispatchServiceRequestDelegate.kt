package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("dispatchServiceRequestDelegate")
class DispatchServiceRequestDelegate(
    private val runtimeService: RuntimeService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val requestType = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_REQUEST_TYPE)

        val targetProcessKey = when (requestType) {
            CamundaOrderProcess.REQUEST_TYPE_PATCH_ROUTE -> CamundaOrderProcess.PROCESS_ROUTE_MANAGE_KEY
            CamundaOrderProcess.REQUEST_TYPE_CANCEL_ORDER,
            CamundaOrderProcess.REQUEST_TYPE_RETRY_PAYMENT,
                -> CamundaOrderProcess.PROCESS_SERVICE_OPERATIONS_KEY
            else -> throw IllegalStateException("Unsupported service request type: $requestType")
        }

        val variables = execution.variables.toMap()
        runtimeService.startProcessInstanceByKey(targetProcessKey, variables)

        log.info(
            "Service request dispatched: clientProcessInstanceId={}, targetProcessKey={}, requestType={}",
            execution.processInstanceId,
            targetProcessKey,
            requestType,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(DispatchServiceRequestDelegate::class.java)
    }
}
