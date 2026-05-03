package com.example.ticket.bpm.delegate

import com.example.ticket.exception.ValidationException
import org.camunda.bpm.engine.delegate.BpmnError
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.DelegateTask

object BpmValidationErrors {
    const val ERROR_CODE = "VALIDATION_ERROR"
    const val VARIABLE = "validationError"
    const val FORM_VARIABLE = "validateError"

    fun throwBpmnError(execution: DelegateExecution, ex: ValidationException): Nothing {
        val message = ex.details.joinToString("; ") { "${it.field}: ${it.issue}" }
        execution.setVariable(VARIABLE, message.ifBlank { ex.message ?: "Invalid request." })
        execution.setVariable(FORM_VARIABLE, message.ifBlank { ex.message ?: "Invalid request." })
        throw BpmnError(ERROR_CODE)
    }

    fun throwBpmnError(task: DelegateTask, ex: ValidationException): Nothing {
        val message = ex.details.joinToString("; ") { "${it.field}: ${it.issue}" }
        task.setVariable(VARIABLE, message.ifBlank { ex.message ?: "Invalid request." })
        task.setVariable(FORM_VARIABLE, message.ifBlank { ex.message ?: "Invalid request." })
        throw BpmnError(ERROR_CODE)
    }
}
