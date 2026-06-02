package com.example.ticket.bpm.delegate

import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ValidationException
import org.camunda.bpm.engine.delegate.DelegateExecution

object BpmVariables {

    fun requiredString(execution: DelegateExecution, name: String): String {
        val value = execution.getVariable(name)
        if (value !is String || value.isBlank()) {
            throw ValidationException(
                details = listOf(ValidationDetail(name, "must be provided")),
            )
        }
        return value.trim()
    }

    fun optionalString(execution: DelegateExecution, name: String): String? {
        val value = execution.getVariable(name) ?: return null
        if (value !is String) {
            throw ValidationException(
                details = listOf(ValidationDetail(name, "must be a string")),
            )
        }
        return value.trim().ifBlank { null }
    }

    fun optionalInt(execution: DelegateExecution, name: String): Int? {
        val value = execution.getVariable(name) ?: return null
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
            else -> null
        } ?: throw ValidationException(
            details = listOf(ValidationDetail(name, "must be an integer")),
        )
    }

    fun optionalBoolean(execution: DelegateExecution, name: String, default: Boolean = false): Boolean {
        val value = execution.getVariable(name) ?: return default
        return when (value) {
            is Boolean -> value
            is String -> value.trim().takeIf { it.isNotBlank() }?.toBooleanStrictOrNull() ?: throw ValidationException(
                details = listOf(ValidationDetail(name, "must be a boolean")),
            )
            else -> throw ValidationException(
                details = listOf(ValidationDetail(name, "must be a boolean")),
            )
        }
    }
}
