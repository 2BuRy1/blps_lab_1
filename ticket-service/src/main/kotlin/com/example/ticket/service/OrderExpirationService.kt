package com.example.ticket.service

import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class OrderExpirationService(
    private val orderRepository: OrderRepository,
    private val ticketProcessService: TicketProcessService,
    private val clock: Clock,
    @Value("\${jobs.order-expiration.created-ttl:15m}")
    private val createdTtl: Duration,
    @Value("\${jobs.order-expiration.pending-3ds-ttl:10m}")
    private val pending3dsTtl: Duration,
    @Value("\${jobs.order-expiration.batch-size:100}")
    private val batchSize: Int,
) {

    fun cancelExpiredCreatedOrders(): Int {
        return cancelExpiredOrders(
            status = OrderStatus.CREATED,
            ttl = createdTtl,
            event = "ORDER_CANCELLED_BY_CREATED_TTL",
        )
    }

    fun cancelExpiredPending3dsOrders(): Int {
        return cancelExpiredOrders(
            status = OrderStatus.PENDING_3DS,
            ttl = pending3dsTtl,
            event = "ORDER_CANCELLED_BY_3DS_TTL",
        )
    }

    private fun cancelExpiredOrders(
        status: OrderStatus,
        ttl: Duration,
        event: String,
    ): Int {
        if (ttl.isZero || ttl.isNegative) {
            log.warn("Skip order expiration for status={}, ttl={} is not positive", status, ttl)
            return 0
        }

        val cutoff = Instant.now(clock).minus(ttl)
        var cancelledCount = 0

        while (true) {
            val expiredOrderIds = orderRepository.findExpiredOrderIds(
                status = status,
                cutoff = cutoff,
                pageable = PageRequest.of(0, batchSize.coerceAtLeast(1)),
            )

            if (expiredOrderIds.isEmpty()) {
                return cancelledCount
            }

            expiredOrderIds.forEach { orderId ->
                val cancelled = ticketProcessService.cancelExpiredOrder(
                    orderId = orderId,
                    expectedStatus = status,
                    event = event,
                )
                if (cancelled) {
                    cancelledCount += 1
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OrderExpirationService::class.java)
    }
}
