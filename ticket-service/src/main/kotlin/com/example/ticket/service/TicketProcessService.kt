package com.example.ticket.service

import com.example.ticket.api.Confirm3dsRequest
import com.example.ticket.api.AsyncOrderOperationAcceptedResponse
import com.example.ticket.api.CancelOrderResponse
import com.example.ticket.api.ConflictError
import com.example.ticket.api.CreateOrderRequest
import com.example.ticket.api.ManagedOrderResponse
import com.example.ticket.api.ManagedRouteResponse
import com.example.ticket.api.NotFoundError
import com.example.ticket.api.OrderCreatedResponse
import com.example.ticket.api.OrderStateResponse
import com.example.ticket.api.Passenger
import com.example.ticket.api.PayOrderRequest
import com.example.ticket.api.PayOrderSuccessResponse
import com.example.ticket.api.RouteOption
import com.example.ticket.api.RoutesResponse
import com.example.ticket.api.Ticket
import com.example.ticket.api.UpdateRouteManageRequest
import com.example.ticket.api.ValidationDetail
import com.example.ticket.client.BankGateway
import com.example.ticket.exception.ConflictException
import com.example.ticket.exception.NotFoundException
import com.example.ticket.exception.ValidationException
import com.example.ticket.persistence.entity.OrderEntity
import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.entity.RouteEntity
import com.example.ticket.persistence.entity.TicketEntity
import com.example.ticket.persistence.repository.OrderRepository
import com.example.ticket.persistence.repository.RouteRepository
import com.example.ticket.persistence.repository.TicketRepository
import com.example.ticket.service.integration.Bitrix24OrderSyncService
import com.example.ticket.service.validation.RouteSearchCriteria
import com.example.ticket.service.validation.RouteSearchValidator
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.access.prepost.PreAuthorize
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Service
class TicketProcessService(
    private val routeRepository: RouteRepository,
    private val orderRepository: OrderRepository,
    private val ticketRepository: TicketRepository,
    private val bankGateway: BankGateway,
    private val bitrix24OrderSyncService: Bitrix24OrderSyncService,
    private val routeSearchValidator: RouteSearchValidator,
    private val transactionManager: PlatformTransactionManager,
    private val clock: Clock,
) {

    private val txTemplate = TransactionTemplate(transactionManager)

    private val readOnlyTxTemplate = TransactionTemplate(transactionManager).apply {
        isReadOnly = true
    }

    fun validateCreateOrderRequestForWorkflow(request: CreateOrderRequest) {
        validateCreateOrderRequest(request)
    }

    fun validatePayOrderRequestForWorkflow(request: PayOrderRequest) {
        validatePayOrderRequest(request)
    }

    fun validateConfirm3dsRequestForWorkflow(request: Confirm3dsRequest) {
        validateConfirm3dsRequest(request)
    }

    @PreAuthorize("hasAuthority('ROUTE_VIEW')")
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
        return readOnlyTxTemplate.execute {

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

            RoutesResponse(
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
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    fun createOrder(request: CreateOrderRequest): OrderCreatedResponse {
        return createOrderInternal(request)
    }

    fun createOrderInternal(request: CreateOrderRequest): OrderCreatedResponse {
        return txTemplate.execute {
            log.info("Create order requested: routeId={}, seat={}, passenger={}", request.routeId, request.seat, request.passenger.fullName)
            validateCreateOrderRequest(request)

            val route = routeRepository.findByRouteIdForUpdate(request.routeId)
                ?: run {
                    log.warn("Create order failed: routeId={} not found", request.routeId)
                    throw NotFoundException(
                        resource = NotFoundError.Resource.ROUTE,
                        message = "Route not found.",
                    )
                }

            if (route.freeSeats <= 0 || route.reservedSeats >= route.capacity) {
                throw ConflictException(
                    code = ConflictError.Code.SEAT_TAKEN,
                    message = "Selected seat is already booked.",
                )
            }

            val seatTaken = orderRepository.existsByRouteRouteIdAndSeatAndStatusIn(
                routeId = route.routeId,
                seat = request.seat,
                statuses = listOf(
                    OrderStatus.CREATED,
                    OrderStatus.PAYMENT_PROCESSING,
                    OrderStatus.PENDING_3DS,
                    OrderStatus.CONFIRMING_3DS,
                    OrderStatus.PAID,
                ),
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
            log.info(
                "Seat reserved for order creation: routeId={}, seat={}, reservedSeats={}, freeSeats={}",
                route.routeId,
                request.seat,
                route.reservedSeats,
                route.freeSeats,
            )

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
            log.info("Order persisted: orderId={}, routeId={}, status={}", order.orderId, route.routeId, order.status)
            order.crmDealId = bitrix24OrderSyncService.createDealForOrder(order)
            orderRepository.save(order)
            log.info("Order synchronized with Bitrix: orderId={}, crmDealId={}", order.orderId, order.crmDealId)

            OrderCreatedResponse(
                orderId = order.orderId,
                status = OrderCreatedResponse.Status.CREATED,
                amount = order.amount,
            )
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    fun payOrder(orderId: String, request: PayOrderRequest): AsyncOrderOperationAcceptedResponse {
        return startPaymentForWorkflow(orderId, request, retryMode = false)
    }

    fun startPaymentForWorkflow(
        orderId: String,
        request: PayOrderRequest,
        retryMode: Boolean,
    ): AsyncOrderOperationAcceptedResponse {
        return txTemplate.execute {
            log.info("Payment requested: orderId={}, retryMode={}", orderId, retryMode)
            validatePayOrderRequest(request)

            val order = orderRepository.findByOrderIdForUpdate(orderId)
                ?: throw NotFoundException(
                    resource = NotFoundError.Resource.ORDER,
                    message = "Order not found.",
                )

            when {
                retryMode -> {
                    when (order.status) {
                        OrderStatus.DECLINED -> reactivateDeclinedOrderForRetry(order)
                        OrderStatus.CREATED -> {}
                        OrderStatus.PAYMENT_PROCESSING,
                        OrderStatus.PENDING_3DS,
                        OrderStatus.CONFIRMING_3DS,
                            -> throw ConflictException(
                            code = ConflictError.Code.ORDER_STATE_INVALID,
                            message = "Order has active payment flow already.",
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
                }

                order.status != OrderStatus.CREATED -> {
                    throw ConflictException(
                        code = ConflictError.Code.ORDER_STATE_INVALID,
                        message = "Order is not in payable state.",
                    )
                }
            }

            transitionOrderStatus(order, OrderStatus.PAYMENT_PROCESSING)
            orderRepository.save(order)
            bitrix24OrderSyncService.syncOrderStateBestEffort(
                order,
                event = if (retryMode) "PAYMENT_RETRY_STARTED" else "PAYMENT_STARTED",
            )
            log.info("Order moved to PAYMENT_PROCESSING: orderId={}, retryMode={}", order.orderId, retryMode)

            if (retryMode) {
                bankGateway.retryAuthorize(amount = order.amount, request = request, orderId = order.orderId)
            } else {
                bankGateway.authorize(amount = order.amount, request = request, orderId = order.orderId)
            }
            log.info("Bank authorize dispatched: orderId={}, retryMode={}", order.orderId, retryMode)

            AsyncOrderOperationAcceptedResponse(
                orderId = order.orderId,
                message = if (retryMode) {
                    "Retry payment request accepted. Poll /orders/${order.orderId}/state for result."
                } else {
                    "Payment request accepted. Poll /orders/${order.orderId}/state for result."
                },
            )
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    fun confirm3ds(orderId: String, request: Confirm3dsRequest): AsyncOrderOperationAcceptedResponse {
        return confirm3dsInternal(orderId, request.code)
    }

    fun confirm3dsInternal(orderId: String, code: String): AsyncOrderOperationAcceptedResponse {
        return txTemplate.execute {
            log.info("3DS confirmation requested: orderId={}", orderId)
            validateConfirm3dsRequest(Confirm3dsRequest(code = code))

            val order = orderRepository.findByOrderIdForUpdate(orderId)
                ?: throw NotFoundException(
                    resource = NotFoundError.Resource.ORDER,
                    message = "Order not found.",
                )

            when (order.status) {
                OrderStatus.PENDING_3DS -> {
                    transitionOrderStatus(order, OrderStatus.CONFIRMING_3DS)
                    orderRepository.save(order)
                }
                OrderStatus.CONFIRMING_3DS -> Unit
                else -> {
                    throw ConflictException(
                        code = ConflictError.Code.ORDER_STATE_INVALID,
                        message = "Order is not waiting for 3DS confirmation.",
                    )
                }
            }

            val paymentId = order.bankPaymentId
                ?: throw ConflictException(
                    code = ConflictError.Code.ORDER_STATE_INVALID,
                    message = "Bank payment id is missing.",
                )

            bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = "THREE_DS_CONFIRMATION_REQUESTED")
            bankGateway.confirm3ds(orderId = order.orderId, paymentId = paymentId, code = code)
            log.info("3DS confirmation forwarded to bank: orderId={}, paymentId={}", order.orderId, paymentId)

            AsyncOrderOperationAcceptedResponse(
                orderId = order.orderId,
                message = "3DS confirmation accepted. Poll /orders/${order.orderId}/state for result.",
            )
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('TICKET_VIEW')")
    fun getTicket(ticketId: String): Ticket {
        return readOnlyTxTemplate.execute {
            val ticket = ticketRepository.findById(ticketId).orElseThrow {
                NotFoundException(
                    resource = NotFoundError.Resource.TICKET,
                    message = "Ticket not found.",
                )
            }

            Ticket(
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
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun getOrderForManage(orderId: String): ManagedOrderResponse {
        return getOrderForManageInternal(orderId)
    }

    fun getOrderForManageInternal(orderId: String): ManagedOrderResponse {
        return readOnlyTxTemplate.execute {
            val order = orderRepository.findById(orderId).orElseThrow {
                NotFoundException(
                    resource = NotFoundError.Resource.ORDER,
                    message = "Order not found.",
                )
            }

            ManagedOrderResponse(
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
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_PAY')")
    fun getOrderState(orderId: String): OrderStateResponse {
        return readOnlyTxTemplate.execute {
            val order = orderRepository.findById(orderId).orElseThrow {
                NotFoundException(
                    resource = NotFoundError.Resource.ORDER,
                    message = "Order not found.",
                )
            }

            OrderStateResponse(
                orderId = order.orderId,
                status = toOrderStateStatus(order.status),
                bankPaymentId = order.bankPaymentId,
                ticketId = order.ticketId,
            )
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun cancelOrder(orderId: String): CancelOrderResponse {
        return cancelOrderInternal(orderId)
    }

    fun cancelOrderInternal(
        orderId: String,
        event: String = "ORDER_CANCELLED",
    ): CancelOrderResponse {
        return txTemplate.execute {
            log.info("Cancel order requested: orderId={}, event={}", orderId, event)
            val order = orderRepository.findByOrderIdForUpdate(orderId)
                ?: throw NotFoundException(
                    resource = NotFoundError.Resource.ORDER,
                    message = "Order not found.",
                )

            when (order.status) {
                OrderStatus.CREATED,
                OrderStatus.PAYMENT_PROCESSING,
                OrderStatus.PENDING_3DS,
                OrderStatus.CONFIRMING_3DS,
                    -> {
                    releaseReservedSeat(order)
                    transitionOrderStatus(order, OrderStatus.CANCELLED)
                    order.bankPaymentId = null
                    orderRepository.save(order)
                    bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = event)
                    log.info("Order cancelled: orderId={}, event={}", order.orderId, event)
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

            CancelOrderResponse(
                orderId = order.orderId,
                status = CancelOrderResponse.Status.CANCELLED,
            )
        } ?: throw IllegalStateException("Transaction returned null")
    }

    @PreAuthorize("hasAuthority('ORDER_MANAGE')")
    fun retryPayment(orderId: String, request: PayOrderRequest): AsyncOrderOperationAcceptedResponse {
        return startPaymentForWorkflow(orderId, request, retryMode = true)
    }

    @PreAuthorize("hasAuthority('ROUTE_MANAGE')")
    fun updateRouteForManage(routeId: String, request: UpdateRouteManageRequest): ManagedRouteResponse {
        return updateRouteForManageInternal(routeId, request)
    }

    fun getRouteForManageInternal(routeId: String): ManagedRouteResponse {
        return readOnlyTxTemplate.execute {
            val route = routeRepository.findById(routeId).orElseThrow {
                NotFoundException(
                    resource = NotFoundError.Resource.ROUTE,
                    message = "Route not found.",
                )
            }

            ManagedRouteResponse(
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
        } ?: throw IllegalStateException("Transaction returned null")
    }

    fun updateRouteForManageInternal(routeId: String, request: UpdateRouteManageRequest): ManagedRouteResponse {
        return txTemplate.execute {
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

            ManagedRouteResponse(
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
        } ?: throw IllegalStateException("Transaction returned null")
    }

    fun issueTicket(order: OrderEntity): PayOrderSuccessResponse {
        val ticket = TicketEntity(
            ticketId = nextTicketId(),
            route = order.route,
            seat = order.seat,
            passportId = order.passportId,
            fullName = order.fullName,
        )
        ticketRepository.save(ticket)

        transitionOrderStatus(order, OrderStatus.PAID)
        order.ticketId = ticket.ticketId
        orderRepository.save(order)
        bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = "TICKET_ISSUED")
        log.info("Ticket issued: orderId={}, ticketId={}", order.orderId, ticket.ticketId)

        return PayOrderSuccessResponse(
            status = PayOrderSuccessResponse.Status.PAID,
            ticketId = ticket.ticketId,
        )
    }

    fun markOrderPending3ds(order: OrderEntity, paymentId: String) {
        transitionOrderStatus(order, OrderStatus.PENDING_3DS)
        order.bankPaymentId = paymentId
        orderRepository.save(order)
        log.info("Order moved to PENDING_3DS: orderId={}, paymentId={}", order.orderId, paymentId)
    }

    fun issueTicketInternal(orderId: String): PayOrderSuccessResponse {
        return txTemplate.execute {
            val order = orderRepository.findByOrderIdForUpdate(orderId)
                ?: throw NotFoundException(
                    resource = NotFoundError.Resource.ORDER,
                    message = "Order not found.",
                )

            if (order.status == OrderStatus.PAID && !order.ticketId.isNullOrBlank()) {
                return@execute PayOrderSuccessResponse(
                    status = PayOrderSuccessResponse.Status.PAID,
                    ticketId = order.ticketId!!,
                )
            }

            if (order.status == OrderStatus.CANCELLED || order.status == OrderStatus.DECLINED) {
                throw ConflictException(
                    code = ConflictError.Code.ORDER_STATE_INVALID,
                    message = "Inactive order cannot be issued.",
                )
            }

            issueTicket(order)
        } ?: throw IllegalStateException("Transaction returned null")
    }

    fun markOrderPending3dsInternal(orderId: String, paymentId: String) {
        txTemplate.executeWithoutResult {
            val order = orderRepository.findByOrderIdForUpdate(orderId) ?: return@executeWithoutResult
            if (order.status == OrderStatus.PAID || order.status == OrderStatus.CANCELLED) {
                return@executeWithoutResult
            }
            markOrderPending3ds(order, paymentId)
            bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = "THREE_DS_REQUIRED")
        }
    }

    fun compensateDeclinedOrder(order: OrderEntity) {
        if (order.status == OrderStatus.DECLINED || order.status == OrderStatus.CANCELLED) {
            return
        }
        if (order.status == OrderStatus.PAID) {
            throw ConflictException(
                code = ConflictError.Code.ORDER_STATE_INVALID,
                message = "Paid order cannot be declined.",
            )
        }

        releaseReservedSeat(order)
        transitionOrderStatus(order, OrderStatus.DECLINED)
        order.bankPaymentId = null
        orderRepository.save(order)
        bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = "PAYMENT_DECLINED")
        log.info("Order declined and compensated: orderId={}", order.orderId)
    }

    fun compensateDeclinedOrderInternal(orderId: String) {
        txTemplate.executeWithoutResult {
            val order = orderRepository.findByOrderIdForUpdate(orderId) ?: return@executeWithoutResult
            compensateDeclinedOrder(order)
        }
    }

    fun reactivateDeclinedOrderForRetry(order: OrderEntity) {
        log.info("Reactivating declined order for retry: orderId={}, routeId={}", order.orderId, order.route.routeId)
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
            statuses = listOf(
                OrderStatus.CREATED,
                OrderStatus.PAYMENT_PROCESSING,
                OrderStatus.PENDING_3DS,
                OrderStatus.CONFIRMING_3DS,
                OrderStatus.PAID,
            ),
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

        transitionOrderStatus(order, OrderStatus.CREATED)
        order.bankPaymentId = null
        order.ticketId = null
        orderRepository.save(order)
        bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = "ORDER_REACTIVATED_FOR_RETRY")
        log.info("Declined order reactivated for retry: orderId={}", order.orderId)
    }

    fun cancelExpiredOrder(orderId: String, expectedStatus: OrderStatus, event: String): Boolean {
        return txTemplate.execute {
            val order = orderRepository.findByOrderIdForUpdate(orderId) ?: return@execute false
            if (order.status != expectedStatus) {
                return@execute false
            }

            releaseReservedSeat(order)
            transitionOrderStatus(order, OrderStatus.CANCELLED)
            order.bankPaymentId = null
            orderRepository.save(order)
            bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = event)
            true
        } ?: false
    }

    fun cancelExpiredOrder(orderId: String, expectedStatus: OrderStatus, event: String): Boolean {
        return txTemplate.execute {
            val order = orderRepository.findByOrderIdForUpdate(orderId) ?: return@execute false
            if (order.status != expectedStatus) {
                return@execute false
            }

            releaseReservedSeat(order)
            transitionOrderStatus(order, OrderStatus.CANCELLED)
            order.bankPaymentId = null
            orderRepository.save(order)
            bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = event)
            true
        } ?: false
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

    private fun transitionOrderStatus(order: OrderEntity, newStatus: OrderStatus) {
        if (order.status != newStatus) {
            log.info("Order status transition: orderId={}, from={}, to={}", order.orderId, order.status, newStatus)
            order.status = newStatus
        }
        order.statusUpdatedAt = Instant.now(clock)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TicketProcessService::class.java)
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
            OrderStatus.PAYMENT_PROCESSING -> ManagedOrderResponse.Status.PAYMENT_PROCESSING
            OrderStatus.PENDING_3DS -> ManagedOrderResponse.Status.PENDING_3DS
            OrderStatus.CONFIRMING_3DS -> ManagedOrderResponse.Status.CONFIRMING_3DS
            OrderStatus.PAID -> ManagedOrderResponse.Status.PAID
            OrderStatus.DECLINED -> ManagedOrderResponse.Status.DECLINED
            OrderStatus.CANCELLED -> ManagedOrderResponse.Status.CANCELLED
        }
    }

    private fun toOrderStateStatus(status: OrderStatus): OrderStateResponse.Status {
        return when (status) {
            OrderStatus.CREATED -> OrderStateResponse.Status.CREATED
            OrderStatus.PAYMENT_PROCESSING -> OrderStateResponse.Status.PAYMENT_PROCESSING
            OrderStatus.PENDING_3DS -> OrderStateResponse.Status.PENDING_3DS
            OrderStatus.CONFIRMING_3DS -> OrderStateResponse.Status.CONFIRMING_3DS
            OrderStatus.PAID -> OrderStateResponse.Status.PAID
            OrderStatus.DECLINED -> OrderStateResponse.Status.DECLINED
            OrderStatus.CANCELLED -> OrderStateResponse.Status.CANCELLED
        }
    }

    private fun nextOrderId(): String = "o${UUID.randomUUID().toString().replace("-", "").take(12)}"

    private fun nextTicketId(): String = "tk${UUID.randomUUID().toString().replace("-", "").take(12)}"
}
