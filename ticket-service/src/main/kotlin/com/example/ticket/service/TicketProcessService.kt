package com.example.ticket.service

import com.example.ticket.api.Confirm3dsRequest
import com.example.ticket.api.CancelOrderResponse
import com.example.ticket.api.ConflictError
import com.example.ticket.api.CreateOrderRequest
import com.example.ticket.api.ManagedOrderResponse
import com.example.ticket.api.ManagedRouteResponse
import com.example.ticket.api.NotFoundError
import com.example.ticket.api.OrderCreatedResponse
import com.example.ticket.api.Passenger
import com.example.ticket.api.PayOrderPending3dsResponse
import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.PayOrderSuccessResponse
import com.example.ticket.api.RouteOption
import com.example.ticket.api.RoutesResponse
import com.example.ticket.api.Ticket
import com.example.ticket.api.UpdateRouteManageRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.client.BankGateway
import com.example.ticket.client.BankPayDecision
import com.example.ticket.exception.ConflictException
import com.example.ticket.exception.IntegrationUnavailableException
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
import org.springframework.security.access.prepost.PreAuthorize
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

    @PreAuthorize("hasAuthority('ROUTE_VIEW')")
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

    @PreAuthorize("hasAuthority('ORDER_CREATE')")
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
            statuses = listOf(OrderStatus.CREATED, OrderStatus.PENDING_3DS, OrderStatus.PAID),
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

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    @Transactional(noRollbackFor = [PaymentDeclinedException::class])
    fun payOrder(orderId: String, request: PayOrderRequest): Any {
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

        return processPayment(order, request)
    }

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    @Transactional(noRollbackFor = [PaymentDeclinedException::class])
    fun confirm3ds(orderId: String, request: Confirm3dsRequest): PayOrderSuccessResponse {

        validateConfirm3dsRequest(request)

        val order = orderRepository.findByOrderIdForUpdate(orderId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ORDER,
                message = "Order not found.",
            )

        if (order.status != OrderStatus.PENDING_3DS) {
            throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Order is not waiting for 3DS confirmation.",
            )
        }

        val paymentId = order.bankPaymentId
            ?: throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Bank payment id is missing.",
            )

        val bankResult = try {
            bankGateway.confirm3ds(paymentId = paymentId, code = request.code)
        } catch (_: IntegrationUnavailableException) {
            declineOrder(order, "BANK_UNAVAILABLE")
        }
        return when (bankResult.status) {
            BankPayDecision.Status.SUCCESS -> issueTicket(order)
            BankPayDecision.Status.DECLINED -> declineOrder(order, bankResult.reason)
            BankPayDecision.Status.REQUIRES_3DS -> throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "3DS confirmation is still pending.",
            )
        }
    }

    @PreAuthorize("hasAuthority('TICKET_VIEW')")
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

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    @Transactional(readOnly = true)
    fun getOrderForManage(orderId: String): ManagedOrderResponse {
        val order = orderRepository.findById(orderId).orElseThrow {
            NotFoundException(
                resource = NotFoundError.Resource.ORDER,
                message = "Order not found.",
            )
        }

        return ManagedOrderResponse(
            orderId = order.orderId,
            routeId = order.route.routeId,
            seat = order.seat,
            amount = order.amount,
            status = toManagedOrderStatus(order.status),
            bankPaymentId = order.bankPaymentId,
            ticketId = order.ticketId,
            passenger = Passenger(
                passportId = order.passportId,
                fullName = order.fullName,
            ),
        )
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    @Transactional
    fun cancelOrder(orderId: String): CancelOrderResponse {
        val order = orderRepository.findByOrderIdForUpdate(orderId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ORDER,
                message = "Order not found.",
            )

        when (order.status) {
            OrderStatus.CREATED,
            OrderStatus.PENDING_3DS,
            -> {
                releaseReservedSeat(order)
                order.status = OrderStatus.CANCELLED
                order.bankPaymentId = null
                orderRepository.save(order)
            }

            OrderStatus.PAID -> throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Paid order cannot be cancelled in this flow.",
            )

            OrderStatus.DECLINED,
            OrderStatus.CANCELLED,
            -> throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Order is already inactive.",
            )
        }

        return CancelOrderResponse(
            orderId = order.orderId,
            status = CancelOrderResponse.Status.CANCELLED,
        )
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    @Transactional(noRollbackFor = [PaymentDeclinedException::class])
    fun retryPayment(orderId: String, request: PayOrderRequest): Any {
        validatePayOrderRequest(request)

        val order = orderRepository.findByOrderIdForUpdate(orderId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ORDER,
                message = "Order not found.",
            )

        when (order.status) {
            OrderStatus.DECLINED -> reactivateDeclinedOrderForRetry(order)
            OrderStatus.CREATED -> {}
            OrderStatus.PENDING_3DS -> throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Order is already waiting for 3DS confirmation.",
            )

            OrderStatus.PAID -> throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Order is already paid.",
            )

            OrderStatus.CANCELLED -> throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Cancelled order cannot be retried.",
            )
        }

        return processPayment(order, request)
    }

    @PreAuthorize("hasAuthority('ROUTE_MANAGE')")
    @Transactional
    fun updateRouteForManage(routeId: String, request: UpdateRouteManageRequest): ManagedRouteResponse {
        val route = routeRepository.findByRouteIdForUpdate(routeId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ROUTE,
                message = "Route not found.",
            )

        val details = mutableListOf<ValidationDetail>()

        if (
            request.from == null &&
            request.to == null &&
            request.date == null &&
            request.fromTerminal == null &&
            request.toTerminal == null &&
            request.trainId == null &&
            request.departureTime == null &&
            request.price == null &&
            request.capacity == null &&
            request.freeSeats == null
        ) {
            details += ValidationDetail("request", "at least one field must be provided")
        }

        var newFrom = route.fromCity
        var newTo = route.toCity
        var newDate = route.travelDate
        var newFromTerminal = route.fromTerminal
        var newToTerminal = route.toTerminal
        var newTrainId = route.trainId
        var newDepartureTime = route.departureTime
        var newPrice = route.price
        var newCapacity = route.capacity
        var newFreeSeats = route.freeSeats
        var freeSeatsExplicitlySet = false

        request.from?.let {
            if (it.isBlank()) {
                details += ValidationDetail("from", "must not be blank")
            } else {
                newFrom = it.trim()
            }
        }
        request.to?.let {
            if (it.isBlank()) {
                details += ValidationDetail("to", "must not be blank")
            } else {
                newTo = it.trim()
            }
        }
        request.date?.let {
            runCatching { LocalDate.parse(it) }
                .onSuccess { parsedDate -> newDate = parsedDate }
                .onFailure { details += ValidationDetail("date", "must be in YYYY-MM-DD format") }
        }
        request.fromTerminal?.let { newFromTerminal = it.ifBlank { null } }
        request.toTerminal?.let { newToTerminal = it.ifBlank { null } }
        request.trainId?.let {
            if (it.isBlank()) {
                details += ValidationDetail("trainId", "must not be blank")
            } else {
                newTrainId = it.trim()
            }
        }
        request.departureTime?.let {
            runCatching { LocalTime.parse(it) }
                .onSuccess { parsedTime -> newDepartureTime = parsedTime }
                .onFailure {
                    details += ValidationDetail(
                        "departure_time",
                        "must be in HH:mm or HH:mm:ss format",
                    )
                }
        }
        request.price?.let {
            if (it < 0) {
                details += ValidationDetail("price", "must be >= 0")
            } else {
                newPrice = it
            }
        }
        request.capacity?.let {
            if (it < 0) {
                details += ValidationDetail("capacity", "must be >= 0")
            } else {
                newCapacity = it
            }
        }
        request.freeSeats?.let {
            freeSeatsExplicitlySet = true
            if (it < 0) {
                details += ValidationDetail("free_seats", "must be >= 0")
            } else {
                newFreeSeats = it
            }
        }

        if (!freeSeatsExplicitlySet && request.capacity != null) {
            newFreeSeats = (newCapacity - route.reservedSeats).coerceAtLeast(0)
        }

        if (newCapacity < route.reservedSeats) {
            details += ValidationDetail(
                "capacity",
                "must be >= reserved_seats (${route.reservedSeats})",
            )
        }
        if (newFreeSeats + route.reservedSeats > newCapacity) {
            details += ValidationDetail(
                "free_seats",
                "reserved_seats + free_seats must be <= capacity",
            )
        }

        if (details.isNotEmpty()) {
            throw ValidationException(details = details)
        }

        route.fromCity = newFrom
        route.toCity = newTo
        route.travelDate = newDate
        route.fromTerminal = newFromTerminal
        route.toTerminal = newToTerminal
        route.trainId = newTrainId
        route.departureTime = newDepartureTime
        route.price = newPrice
        route.capacity = newCapacity
        route.freeSeats = newFreeSeats
        routeRepository.save(route)

        return ManagedRouteResponse(
            routeId = route.routeId,
            from = route.fromCity,
            to = route.toCity,
            date = route.travelDate,
            fromTerminal = route.fromTerminal,
            toTerminal = route.toTerminal,
            trainId = route.trainId,
            departureTime = route.departureTime.toString(),
            price = route.price,
            capacity = route.capacity,
            reservedSeats = route.reservedSeats,
            freeSeats = route.freeSeats,
        )
    }

    private fun processPayment(order: OrderEntity, request: PayOrderRequest): Any {
        val bankResult = try {
            bankGateway.authorize(amount = order.amount, request = request)
        } catch (_: IntegrationUnavailableException) {
            declineOrder(order, "BANK_UNAVAILABLE")
        }

        return when (bankResult.status) {
            BankPayDecision.Status.SUCCESS -> issueTicket(order)

            BankPayDecision.Status.REQUIRES_3DS -> {
                val paymentId = bankResult.paymentId
                    ?: throw IllegalStateException("Bank response missing payment_id for REQUIRES_3DS")

                order.status = OrderStatus.PENDING_3DS
                order.bankPaymentId = paymentId
                orderRepository.save(order)

                PayOrderPending3dsResponse(
                    status = PayOrderPending3dsResponse.Status.PENDING_3DS,
                    paymentId = paymentId,
                    message = bankResult.challengeMessage ?: "Confirm 3DS challenge",
                )
            }

            BankPayDecision.Status.DECLINED -> {
                declineOrder(order, bankResult.reason)
            }
        }
    }

    private fun issueTicket(order: OrderEntity): PayOrderSuccessResponse {
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

    private fun declineOrder(order: OrderEntity, reason: String?): Nothing {
        releaseReservedSeat(order)
        order.status = OrderStatus.DECLINED
        orderRepository.save(order)

        throw PaymentDeclinedException(
            "Payment was declined by the bank${reason?.let { ": $it" } ?: "."}",
        )
    }

    private fun reactivateDeclinedOrderForRetry(order: OrderEntity) {
        val route = routeRepository.findByRouteIdForUpdate(order.route.routeId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ROUTE,
                message = "Route not found.",
            )

        if (route.freeSeats <= 0 || route.reservedSeats >= route.capacity) {
            throw ConflictException(
                code = ConflictError.Code.SEAT_TAKEN,
                message = "No seats available for retry.",
            )
        }

        val seatTaken = orderRepository.existsByRouteRouteIdAndSeatAndStatusIn(
            routeId = route.routeId,
            seat = order.seat,
            statuses = listOf(OrderStatus.CREATED, OrderStatus.PENDING_3DS, OrderStatus.PAID),
        )
        if (seatTaken) {
            throw ConflictException(
                code = ConflictError.Code.SEAT_TAKEN,
                message = "Seat is occupied by another active order.",
            )
        }

        route.reservedSeats += 1
        route.freeSeats = (route.freeSeats - 1).coerceAtLeast(0)
        routeRepository.save(route)

        order.status = OrderStatus.CREATED
        order.bankPaymentId = null
        order.ticketId = null
        orderRepository.save(order)
    }

    private fun releaseReservedSeat(order: OrderEntity) {
        val route = routeRepository.findByRouteIdForUpdate(order.route.routeId)
            ?: throw NotFoundException(
                resource = NotFoundError.Resource.ROUTE,
                message = "Route not found.",
            )

        route.reservedSeats = (route.reservedSeats - 1).coerceAtLeast(0)
        route.freeSeats = (route.freeSeats + 1).coerceAtMost(route.capacity)
        routeRepository.save(route)
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
                predicates += cb.equal(cb.upper(root.get<String>("toTerminal")), it.uppercase())
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
        val passportRegex = Regex("^\\d{4}\\s?\\d{6}$")

        if (request.routeId.isBlank()) {
            details += ValidationDetail("routeId", "must not be blank")
        }
        if (!request.seat.matches(Regex("^\\d{1,2}[A-Z]$"))) {
            details += ValidationDetail("seat", "must match format like 12A")
        }
        if (request.passenger.passportId.isBlank()) {
            details += ValidationDetail("passenger.passportId", "must not be blank")
        } else if (!request.passenger.passportId.matches(passportRegex)) {
            details += ValidationDetail(
                "passenger.passportId",
                "must match format 1234 567890 or 1234567890",
            )
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

    private fun validateConfirm3dsRequest(request: Confirm3dsRequest) {
        if (!request.code.matches(Regex("^\\d{6}$"))) {
            throw ValidationException(
                details = listOf(ValidationDetail("code", "must be 6 digits")),
            )
        }
    }

    private fun toManagedOrderStatus(status: OrderStatus): ManagedOrderResponse.Status {
        return when (status) {
            OrderStatus.CREATED -> ManagedOrderResponse.Status.CREATED
            OrderStatus.PENDING_3DS -> ManagedOrderResponse.Status.PENDING_3DS
            OrderStatus.PAID -> ManagedOrderResponse.Status.PAID
            OrderStatus.DECLINED -> ManagedOrderResponse.Status.DECLINED
            OrderStatus.CANCELLED -> ManagedOrderResponse.Status.CANCELLED
        }
    }


    private fun nextOrderId(): String = "o${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun nextTicketId(): String = "tk${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
