package com.example.ticket.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RoutesResponse(
    val routes: List<RouteOption>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RouteOption(
    val routeId: String,
    val from: String,
    val to: String,
    val date: LocalDate,
    @JsonProperty("from_terminal") val fromTerminal: String? = null,
    @JsonProperty("to_terminal") val toTerminal: String? = null,
    val trainId: String,
    val freeSeats: Int,
    val price: Int,
)


data class CreateOrderRequest(
    val routeId: String,
    val seat: String,
    @field:Valid
    val passenger: Passenger,
)

data class RegisterRequest(
    val username: String,
    val password: String,
    @JsonProperty("password_repeat") val passwordRepeat: String,
)

data class RegisterResponse(
    val username: String,
    val roles: List<String>,
)

data class LoginResponse(
    val username: String,
    val roles: List<String>,
    val privileges: List<String>,
)

data class Passenger(
    @field: NotBlank
    @field: Size(min = 10, max = 11)
    @field: Pattern(regexp = "^\\d{4}\\s?\\d{6}$")
    val passportId: String,

    @field: Size(max = 255)
    val fullName: String,
)

data class OrderCreatedResponse(
    val orderId: String,
    val status: Status,
    val amount: Int,
) {
    enum class Status {
        CREATED,
    }
}

data class PayOrderRequest(
    @JsonProperty("card_number") val cardNumber: String,
    @JsonProperty("expiration_date") val expirationDate: String,
    val cvv: String,
)

data class PayOrderSuccessResponse(
    val status: Status,
    val ticketId: String,
) {
    enum class Status {
        PAID,
    }
}

data class PayOrderPending3dsResponse(
    val status: Status,
    val paymentId: String,
    val message: String? = null,
) {
    enum class Status {
        PENDING_3DS,
    }
}

data class Confirm3dsRequest(
    val code: String,
)

data class AsyncOrderOperationAcceptedResponse(
    val status: Status = Status.ACCEPTED,
    val orderId: String,
    val message: String,
) {
    enum class Status {
        ACCEPTED,
    }
}

data class OrderStateResponse(
    val orderId: String,
    val status: Status,
    @JsonProperty("bank_payment_id") val bankPaymentId: String? = null,
    @JsonProperty("ticket_id") val ticketId: String? = null,
) {
    enum class Status {
        CREATED,
        PAYMENT_PROCESSING,
        PENDING_3DS,
        CONFIRMING_3DS,
        PAID,
        DECLINED,
        CANCELLED,
    }
}

data class Ticket(
    val ticketId: String,
    val from: String,
    val to: String,
    val date: LocalDate,
    val trainId: String,
    val seat: String,
    val passenger: Passenger,
)

data class ValidationError(
    val type: Type = Type.VALIDATION_ERROR,
    val message: String,
    val details: List<ValidationDetail>,
) {
    enum class Type {
        VALIDATION_ERROR,
    }
}

data class ValidationDetail(
    val field: String,
    val issue: String,
)

data class NotFoundError(
    val type: Type = Type.NOT_FOUND,
    val resource: Resource,
    val message: String,
) {
    enum class Type {
        NOT_FOUND,
    }

    enum class Resource {
        ROUTE,
        ORDER,
        TICKET,
    }
}

data class ConflictError(
    val type: Type = Type.CONFLICT,
    val code: Code,
    val message: String,
) {
    enum class Type {
        CONFLICT,
    }

    enum class Code {
        SEAT_TAKEN,
        ORDER_STATE_INVALID,
        USER_ALREADY_EXISTS,
    }
}

data class PaymentDeclinedError(
    val type: Type = Type.PAYMENT_DECLINED,
    val code: Code = Code.DECLINED,
    val message: String,
) {
    enum class Type {
        PAYMENT_DECLINED,
    }

    enum class Code {
        DECLINED,
    }
}

data class IntegrationUnavailableError(
    val type: Type = Type.INTEGRATION_UNAVAILABLE,
    val service: Service = Service.BANK,
    val message: String,
) {
    enum class Type {
        INTEGRATION_UNAVAILABLE,
    }

    enum class Service {
        BANK,
    }
}

data class ManagedOrderResponse(
    val orderId: String,
    val routeId: String,
    val seat: String,
    val amount: Int,
    val status: Status,
    @JsonProperty("bank_payment_id") val bankPaymentId: String? = null,
    @JsonProperty("ticket_id") val ticketId: String? = null,
    val passenger: Passenger,
) {
    enum class Status {
        CREATED,
        PAYMENT_PROCESSING,
        PENDING_3DS,
        CONFIRMING_3DS,
        PAID,
        DECLINED,
        CANCELLED,
    }
}

data class CancelOrderResponse(
    val orderId: String,
    val status: Status,
) {
    enum class Status {
        CANCELLED,
    }
}

data class UpdateRouteManageRequest(
    val from: String? = null,
    val to: String? = null,
    val date: String? = null,
    @JsonProperty("from_terminal") val fromTerminal: String? = null,
    @JsonProperty("to_terminal") val toTerminal: String? = null,
    val trainId: String? = null,
    @JsonProperty("departure_time") val departureTime: String? = null,
    val price: Int? = null,
    val capacity: Int? = null,
    @JsonProperty("free_seats") val freeSeats: Int? = null,
)

data class ManagedRouteResponse(
    val routeId: String,
    val from: String,
    val to: String,
    val date: LocalDate,
    @JsonProperty("from_terminal") val fromTerminal: String? = null,
    @JsonProperty("to_terminal") val toTerminal: String? = null,
    val trainId: String,
    @JsonProperty("departure_time") val departureTime: String,
    val price: Int,
    val capacity: Int,
    @JsonProperty("reserved_seats") val reservedSeats: Int,
    @JsonProperty("free_seats") val freeSeats: Int,
)
