package com.example.ticket.service

import com.example.ticket.api.ConflictError
import com.example.ticket.api.CreateOrderRequest
import com.example.ticket.api.NotFoundError
import com.example.ticket.api.OrderCreatedResponse
import com.example.ticket.api.Passenger
import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.PayOrderSuccessResponse
import com.example.ticket.api.RouteOption
import com.example.ticket.api.RoutesResponse
import com.example.ticket.api.Ticket
import com.example.ticket.api.ValidationDetail
import com.example.ticket.client.BankGateway
import com.example.ticket.client.BankPayDecision
import com.example.ticket.exception.ConflictException
import com.example.ticket.exception.NotFoundException
import com.example.ticket.exception.PaymentDeclinedException
import com.example.ticket.exception.ValidationException
import com.example.ticket.persistence.entity.OrderEntity
import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.entity.RouteEntity
import com.example.ticket.persistence.entity.TicketEntity
import com.example.ticket.persistence.repository.OrderRepository
import com.example.ticket.persistence.repository.RouteRepository
import com.example.ticket.persistence.repository.TicketRepository
import com.example.ticket.service.validation.RouteSearchCriteria
import com.example.ticket.service.validation.RouteSearchValidator
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Service
class TicketProcessService(
    private val routeRepository: RouteRepository,
    private val orderRepository: OrderRepository,
    private val ticketRepository: TicketRepository,
    private val bankGateway: BankGateway,
    private val routeSearchValidator: RouteSearchValidator,
) {

    @Transactional(readOnly = true)
    fun searchRoutes(
        from: String,
        to: String,
        date: String,
        fromTerminal: String?,
        toTerminal: String?,
        minPrice: Int?,
        maxPrice: Int?,
        departureAfter: String?,
        departureBefore: String?,
        onlyWithSeats: Boolean?,
        sort: String?,
        pageable: Pageable,
    ): RoutesResponse {
        val criteria = routeSearchValidator.validateAndBuild(
            from = from,
            to = to,
            date = date,
            fromTerminal = fromTerminal,
            toTerminal = toTerminal,
            minPrice = minPrice,
            maxPrice = maxPrice,
            departureAfter = departureAfter,
            departureBefore = departureBefore,
            onlyWithSeats = onlyWithSeats,
            sort = sort,
        )

        val pageRequest = PageRequest.of(
            pageable.pageNumber,
            pageable.pageSize,
            criteria.sortMode.toSort(),
        )

        val page = routeRepository.findAll(routeSpecification(criteria), pageRequest)

        return RoutesResponse(
            routes = page.content.map { route ->
                RouteOption(
                    routeId = route.routeId,
                    from = route.fromCity,
                    to = route.toCity,
                    date = route.travelDate,
                    fromTerminal = route.fromTerminal,
                    toTerminal = route.toTerminal,
                    trainId = route.trainId,
                    freeSeats = route.freeSeats.coerceAtLeast(0),
                    price = route.price,
                )
            }
        )
    }

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderCreatedResponse {
        validateCreateOrderRequest(request)

        val route = routeRepository.findByRouteIdForUpdate(request.routeId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ROUTE,
                message = "Route not found.",
            )

        if (route.freeSeats <= 0 || route.reservedSeats >= route.capacity) {
            throw ConflictException(
                code = ConflictError.Code.SEAT_TAKEN,
                message = "Selected seat is already booked.",
            )
        }

        val seatTaken = orderRepository.existsByRouteRouteIdAndSeatAndStatusIn(
            routeId = route.routeId,
            seat = request.seat,
            statuses = listOf(OrderStatus.CREATED, OrderStatus.PAID),
        )
        if (seatTaken) {
            throw ConflictException(
                code = ConflictError.Code.SEAT_TAKEN,
                message = "Selected seat is already booked.",
            )
        }

        route.reservedSeats += 1
        route.freeSeats = (route.freeSeats - 1).coerceAtLeast(0)
        routeRepository.save(route)

        val order = OrderEntity(
            orderId = nextOrderId(),
            route = route,
            seat = request.seat,
            passportId = request.passenger.passportId,
            fullName = request.passenger.fullName,
            amount = route.price,
            status = OrderStatus.CREATED,
        )
        orderRepository.save(order)

        return OrderCreatedResponse(
            orderId = order.orderId,
            status = OrderCreatedResponse.Status.CREATED,
            amount = order.amount,
        )
    }

    @Transactional
    fun payOrder(orderId: String, request: PayOrderRequest): PayOrderSuccessResponse {
        validatePayOrderRequest(request)

        val order = orderRepository.findByOrderIdForUpdate(orderId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ORDER,
                message = "Order not found.",
            )

        if (order.status != OrderStatus.CREATED) {
            throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Order is not in payable state.",
            )
        }

        val bankResult = bankGateway.authorize(amount = order.amount, request = request)
        when (bankResult.status) {
            BankPayDecision.Status.SUCCESS -> {
                val ticket = TicketEntity(
                    ticketId = nextTicketId(),
                    route = order.route,
                    seat = order.seat,
                    passportId = order.passportId,
                    fullName = order.fullName,
                )
                ticketRepository.save(ticket)

                order.status = OrderStatus.PAID
                order.ticketId = ticket.ticketId
                orderRepository.save(order)

                return PayOrderSuccessResponse(
                    status = PayOrderSuccessResponse.Status.PAID,
                    ticketId = ticket.ticketId,
                )
            }

            BankPayDecision.Status.DECLINED -> {
                val route = routeRepository.findByRouteIdForUpdate(order.route.routeId)
                    ?: throw NotFoundException(
                        resource = NotFoundError.Resource.ROUTE,
                        message = "Route not found.",
                    )
                route.reservedSeats = (route.reservedSeats - 1).coerceAtLeast(0)
                route.freeSeats = (route.freeSeats + 1).coerceAtMost(route.capacity)
                routeRepository.save(route)

                order.status = OrderStatus.DECLINED
                orderRepository.save(order)

                throw PaymentDeclinedException(
                    "Payment was declined by the bank${bankResult.reason?.let { ": $it" } ?: "."}",
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun getTicket(ticketId: String): Ticket {
        val ticket = ticketRepository.findById(ticketId).orElseThrow {
            NotFoundException(
                resource = NotFoundError.Resource.TICKET,
                message = "Ticket not found.",
            )
        }

        return Ticket(
            ticketId = ticket.ticketId,
            from = ticket.route.fromCity,
            to = ticket.route.toCity,
            date = ticket.route.travelDate,
            trainId = ticket.route.trainId,
            seat = ticket.seat,
            passenger = Passenger(
                passportId = ticket.passportId,
                fullName = ticket.fullName,
            ),
        )
    }

    private fun routeSpecification(criteria: RouteSearchCriteria): Specification<RouteEntity> {
        return Specification { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            predicates += cb.equal(cb.upper(root.get("fromCity")), criteria.from.uppercase())
            predicates += cb.equal(cb.upper(root.get("toCity")), criteria.to.uppercase())
            predicates += cb.equal(root.get<LocalDate>("travelDate"), criteria.travelDate)

            criteria.fromTerminal?.let {
                predicates += cb.equal(cb.upper(root.get("fromTerminal")), it.uppercase())
            }
            criteria.toTerminal?.let {
                predicates += cb.equal(cb.upper(root.get("toTerminal")), it.uppercase())
            }
            criteria.minPrice?.let {
                predicates += cb.greaterThanOrEqualTo(root.get("price"), it)
            }
            criteria.maxPrice?.let {
                predicates += cb.lessThanOrEqualTo(root.get("price"), it)
            }
            criteria.departureAfter?.let {
                predicates += cb.greaterThanOrEqualTo(root.get("departureTime"), it)
            }
            criteria.departureBefore?.let {
                predicates += cb.lessThanOrEqualTo(root.get("departureTime"), it)
            }
            if (criteria.onlyWithSeats) {
                predicates += cb.greaterThan(root.get("freeSeats"), 0)
            }

            cb.and(*predicates.toTypedArray())
        }
    }

    private fun validateCreateOrderRequest(request: CreateOrderRequest) {
        val details = mutableListOf<ValidationDetail>()

        if (request.routeId.isBlank()) {
            details += ValidationDetail("routeId", "must not be blank")
        }
        if (!request.seat.matches(Regex("^\\d{1,2}[A-Z]$"))) {
            details += ValidationDetail("seat", "must match format like 12A")
        }
        if (request.passenger.passportId.isBlank()) {
            details += ValidationDetail("passenger.passportId", "must not be blank")
        }
        if (request.passenger.fullName.isBlank()) {
            details += ValidationDetail("passenger.fullName", "must not be blank")
        }

        if (details.isNotEmpty()) {
            throw ValidationException(details = details)
        }
    }

    private fun validatePayOrderRequest(request: PayOrderRequest) {
        val details = mutableListOf<ValidationDetail>()

        if (!request.cardNumber.matches(Regex("^\\d{16}$"))) {
            details += ValidationDetail("card_number", "must be 16 digits")
        }
        if (!request.expirationDate.matches(Regex("^(0[1-9]|1[0-2])/[0-9]{2}$"))) {
            details += ValidationDetail("expiration_date", "must be in MM/YY format")
        }
        if (!request.cvv.matches(Regex("^\\d{3}$"))) {
            details += ValidationDetail("cvv", "must be 3 digits")
        }

        if (details.isNotEmpty()) {
            throw ValidationException(details = details)
        }
    }

    private fun nextOrderId(): String = "o${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun nextTicketId(): String = "tk${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
