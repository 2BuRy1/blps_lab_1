package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component("prepareClientPaymentPathDelegate")
class PrepareClientPaymentPathDelegate : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        execution.setVariable(CamundaOrderProcess.VAR_CLIENT_PREDICTED_3DS, false)
        log.info(
            "Client process disabled speculative 3DS prediction: clientProcessInstanceId={}",
            execution.processInstanceId,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(PrepareClientPaymentPathDelegate::class.java)
    }
}
