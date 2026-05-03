package com.example.ticket.bpm.delegate

import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ValidationException
import org.camunda.bpm.engine.delegate.DelegateTask
import org.camunda.bpm.engine.delegate.TaskListener
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component("formDataValidator")
class FormDataValidator : TaskListener {

    override fun notify(delegateTask: DelegateTask) {
        try {
            validate(delegateTask)
            clearValidationError(delegateTask)
        } catch (ex: ValidationException) {
            BpmValidationErrors.throwBpmnError(delegateTask, ex)
        }
    }

    private fun validate(task: DelegateTask) {
        val details = mutableListOf<ValidationDetail>()
        when (task.taskDefinitionKey) {
            TASK_SERVICE_REQUEST -> validateServiceRequest(task, details)
            TASK_ENTER_CARD -> validateCard(task, details)
            TASK_SELECT_SEAT -> validateSeat(task, details)
            TASK_ENTER_PASSENGER -> validatePassenger(task, details)
            TASK_ENTER_ROUTE -> validateRouteSearch(task, details)
            TASK_CONFIRM_ORDER -> validateConfirmation(task, details)
            TASK_REGISTER_CLIENT -> validateRegistration(task, details)
            TASK_LOGIN_CLIENT -> validateLogin(task, details)
            TASK_CLIENT_CONFIRM_3DS,
            TASK_SERVER_CONFIRM_3DS_WITH_BANK,
                -> validate3ds(task, details)
        }

        if (details.isNotEmpty()) {
            throw ValidationException(details = details)
        }
    }

    private fun validateServiceRequest(task: DelegateTask, details: MutableList<ValidationDetail>) {
        val requestType = requiredText(task, "requestType", details)
        requiredText(task, "orderId", details)?.let {
            if (!it.matches(ORDER_ID_REGEX)) {
                details += ValidationDetail("orderId", "must match format o + 12 lowercase hex chars")
            }
        }

        if (requestType != null && requestType !in SERVICE_REQUEST_TYPES) {
            details += ValidationDetail("requestType", "must be CANCEL_ORDER or RETRY_PAYMENT")
        }
        if (requestType == "RETRY_PAYMENT") {
            validateCard(task, details)
        }
    }

    private fun validateCard(task: DelegateTask, details: MutableList<ValidationDetail>) {
        requiredText(task, "cardNumber", details)?.let {
            if (!it.matches(CARD_NUMBER_REGEX)) {
                details += ValidationDetail("cardNumber", "must be 16 digits")
            }
        }
        requiredText(task, "expirationDate", details)?.let {
            if (!it.matches(EXPIRATION_DATE_REGEX)) {
                details += ValidationDetail("expirationDate", "must be in MM/YY format")
            }
        }
        requiredText(task, "cvv", details)?.let {
            if (!it.matches(CVV_REGEX)) {
                details += ValidationDetail("cvv", "must be 3 digits")
            }
        }
    }

    private fun validateSeat(task: DelegateTask, details: MutableList<ValidationDetail>) {
        requiredText(task, "routeId", details)
        requiredText(task, "seat", details)?.let {
            if (!it.matches(SEAT_REGEX)) {
                details += ValidationDetail("seat", "must match format like 12A")
            }
        }
    }

    private fun validatePassenger(task: DelegateTask, details: MutableList<ValidationDetail>) {
        requiredText(task, "passengerFullName", details)?.let {
            if (it.length > 255) {
                details += ValidationDetail("passengerFullName", "must be at most 255 characters")
            }
        }
        requiredText(task, "passengerPassportId", details)?.let {
            if (!it.matches(PASSPORT_REGEX)) {
                details += ValidationDetail("passport ID", "must match format 1234 567890 or 1234567890")
            }
        }
        optionalText(task, "email", details)?.let {
            if (!it.matches(EMAIL_REGEX)) {
                details += ValidationDetail("email", "must be a valid email address")
            }
        }
        optionalText(task, "phone", details)?.let {
            if (!it.matches(PHONE_REGEX)) {
                details += ValidationDetail("phone", "must contain 10-15 digits and may start with +")
            }
        }
    }

    private fun validateRouteSearch(task: DelegateTask, details: MutableList<ValidationDetail>) {
        requiredText(task, "from", details)
        requiredText(task, "to", details)
        requiredText(task, "travelDate", details)?.let {
            if (runCatching { LocalDate.parse(it) }.isFailure) {
                details += ValidationDetail("travelDate", "must be in YYYY-MM-DD format")
            }
        }
    }

    private fun validateConfirmation(task: DelegateTask, details: MutableList<ValidationDetail>) {
        val value = task.getVariable("orderConfirmed")
        if (value != true) {
            details += ValidationDetail("orderConfirmed", "must be checked")
        }
    }

    private fun validateRegistration(task: DelegateTask, details: MutableList<ValidationDetail>) {
        validateUsernamePassword(task, details)
        val password = optionalText(task, "password", details)
        val passwordRepeat = requiredText(task, "passwordRepeat", details)
        if (password != null && passwordRepeat != null && password != passwordRepeat) {
            details += ValidationDetail("passwordRepeat", "must match password")
        }
    }

    private fun validateLogin(task: DelegateTask, details: MutableList<ValidationDetail>) {
        validateUsernamePassword(task, details)
    }

    private fun validateUsernamePassword(task: DelegateTask, details: MutableList<ValidationDetail>) {
        requiredText(task, "username", details)?.let {
            if (!it.matches(USERNAME_REGEX)) {
                details += ValidationDetail("username", "must be 3-64 chars and contain only letters or digits")
            }
        }
        requiredText(task, "password", details)?.let {
            if (it.length !in 8..128) {
                details += ValidationDetail("password", "must be 8-128 characters")
            }
        }
    }

    private fun validate3ds(task: DelegateTask, details: MutableList<ValidationDetail>) {
        requiredText(task, "threeDsCode", details)?.let {
            if (!it.matches(THREE_DS_REGEX)) {
                details += ValidationDetail("threeDsCode", "must be 6 digits")
            }
        }
    }

    private fun requiredText(task: DelegateTask, name: String, details: MutableList<ValidationDetail>): String? {
        val value = optionalText(task, name, details)
        if (value == null) {
            details += ValidationDetail(name, "must be provided")
        }
        return value
    }

    private fun optionalText(task: DelegateTask, name: String, details: MutableList<ValidationDetail>): String? {
        val value = task.getVariable(name) ?: return null
        if (value !is String) {
            details += ValidationDetail(name, "must be a string")
            return null
        }
        return value.trim().ifBlank { null }
    }

    private fun clearValidationError(task: DelegateTask) {
        task.removeVariable(BpmValidationErrors.VARIABLE)
        task.removeVariable(BpmValidationErrors.FORM_VARIABLE)
    }

    private companion object {
        private const val TASK_SERVICE_REQUEST = "Task_Operator_SubmitServiceRequest"
        private const val TASK_ENTER_CARD = "Task_Client_EnterCard"
        private const val TASK_SELECT_SEAT = "Task_Client_SelectSeat"
        private const val TASK_ENTER_PASSENGER = "Task_Client_EnterPassenger"
        private const val TASK_ENTER_ROUTE = "Task_Client_EnterRoute"
        private const val TASK_CONFIRM_ORDER = "Task_Client_ConfirmOrder"
        private const val TASK_REGISTER_CLIENT = "Task_Client_Register"
        private const val TASK_LOGIN_CLIENT = "Task_Client_Login"
        private const val TASK_CLIENT_CONFIRM_3DS = "Task_Client_Confirm3DS"
        private const val TASK_SERVER_CONFIRM_3DS_WITH_BANK = "Task_Server_Confirm3DSWithBank"

        private val ORDER_ID_REGEX = Regex("^o[0-9a-f]{12}$")
        private val CARD_NUMBER_REGEX = Regex("^\\d{16}$")
        private val EXPIRATION_DATE_REGEX = Regex("^(0[1-9]|1[0-2])/[0-9]{2}$")
        private val CVV_REGEX = Regex("^\\d{3}$")
        private val SEAT_REGEX = Regex("^\\d{1,2}[A-Z]$")
        private val PASSPORT_REGEX = Regex("^\\d{4}\\s?\\d{6}$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val PHONE_REGEX = Regex("^\\+?[0-9]{10,15}$")
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9]{3,64}$")
        private val THREE_DS_REGEX = Regex("^\\d{6}$")
        private val SERVICE_REQUEST_TYPES = setOf("CANCEL_ORDER", "RETRY_PAYMENT")
    }
}
