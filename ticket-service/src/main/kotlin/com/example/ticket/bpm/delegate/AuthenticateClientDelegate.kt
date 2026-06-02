package com.example.ticket.bpm.delegate

import com.example.ticket.api.RegisterRequest
import com.example.ticket.exception.ConflictException
import com.example.ticket.exception.ValidationException
import com.example.ticket.service.AuthService
import org.camunda.bpm.engine.delegate.BpmnError
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.springframework.stereotype.Component

@Component("authenticateClientDelegate")
class AuthenticateClientDelegate(
    private val authService: AuthService,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val username = BpmVariables.requiredString(execution, "username")
        val password = BpmVariables.requiredString(execution, "password")

        val authResult = try {
            val registeredUser = BpmVariables.optionalBoolean(execution, "registeredUser")
            if (registeredUser) {
                authService.verifyClientCredentials(username, password)
            } else {
                val passwordRepeat = BpmVariables.requiredString(execution, "passwordRepeat")
                val registered = authService.register(
                    RegisterRequest(
                        username = username,
                        password = password,
                        passwordRepeat = passwordRepeat,
                    ),
                )
                authService.verifyClientCredentials(registered.username, password)
            }
        } catch (ex: ValidationException) {
            execution.setVariable("authenticationError", ex.details.joinToString("; ") { "${it.field}: ${it.issue}" })
            throw BpmnError(AUTH_FAILED)
        } catch (ex: ConflictException) {
            execution.setVariable("authenticationError", ex.message)
            throw BpmnError(AUTH_FAILED)
        }

        execution.removeVariable("authenticationError")
        execution.setVariable("authenticatedUsername", authResult.username)
        execution.setVariable("authenticatedRoles", authResult.roles.joinToString(","))
    }

    companion object {
        private const val AUTH_FAILED = "AUTH_FAILED"
    }
}
