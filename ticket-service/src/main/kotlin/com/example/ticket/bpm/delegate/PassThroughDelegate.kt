package com.example.ticket.bpm.delegate

import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("passThroughDelegate")
class PassThroughDelegate : JavaDelegate {
    override fun execute(execution: DelegateExecution) = Unit
}
