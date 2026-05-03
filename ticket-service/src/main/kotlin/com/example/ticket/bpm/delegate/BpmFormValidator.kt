package com.example.ticket.bpm.delegate

import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ValidationException
import com.fasterxml.jackson.databind.ObjectMapper
import org.camunda.bpm.engine.delegate.DelegateExecution

object BpmFormValidator {

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRegex = Regex("^\\+?[0-9]{10,15}$")

    fun validateOptionalPassengerContacts(execution: DelegateExecution, objectMapper: ObjectMapper? = null) {
        val details = mutableListOf<ValidationDetail>()
        BpmVariables.optionalString(execution, "email")?.let {
            if (!it.matches(emailRegex)) {
                details += ValidationDetail("email", "must be a valid email address")
            }
        }
        BpmVariables.optionalString(execution, "phone")?.let {
            if (!it.matches(phoneRegex)) {
                details += ValidationDetail("phone", "must contain 10-15 digits and may start with +")
            }
        }
        if (details.isNotEmpty()) {
            objectMapper?.let {
                execution.setVariable("validateError", it.writeValueAsString(details))
            }
            throw ValidationException(details = details)
        }
    }
}
