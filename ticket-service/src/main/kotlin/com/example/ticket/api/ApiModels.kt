package com.example.ticket.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
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
    val passenger: Passenger,
)

data class Passenger(
    val passportId: String,
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
