package com.example.ticket.service.integration

import com.example.ticket.client.bitrix.Bitrix24DealSyncPayload
import com.example.ticket.client.bitrix.Bitrix24JcaClient
import com.example.ticket.persistence.entity.OrderEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Bitrix24OrderSyncService(
    private val bitrix24JcaClient: Bitrix24JcaClient,
    @Value("\${integration.bitrix.enabled:true}")
    private val enabled: Boolean,
) {

    fun createDealForOrder(order: OrderEntity): Long? {
        if (!enabled) {
            log.info("Bitrix24 integration disabled, skip create deal for orderId={}", order.orderId)
            return null
        }

        log.info("Bitrix24 create deal started: orderId={}", order.orderId)
        return runCatching {
            bitrix24JcaClient.createDeal(payloadFrom(order, event = "ORDER_CREATED"))
        }.onSuccess { dealId ->
            log.info("Bitrix24 create deal finished: orderId={}, dealId={}", order.orderId, dealId)
        }.onFailure { ex ->
            log.error("Bitrix24 create deal failed: orderId={}, reason={}", order.orderId, ex.message, ex)
        }.getOrNull()
    }

    fun syncOrderStateBestEffort(order: OrderEntity, event: String) {
        if (!enabled) {
            log.info("Bitrix24 integration disabled, skip sync for orderId={}, event={}", order.orderId, event)
            return
        }

        val dealId = order.crmDealId
        if (dealId == null) {
            log.warn("Bitrix24 deal id is missing, skip sync for orderId={}, event={}", order.orderId, event)
            return
        }

        runCatching {
            log.info("Bitrix24 sync started: orderId={}, dealId={}, event={}", order.orderId, dealId, event)
            bitrix24JcaClient.updateDeal(dealId, payloadFrom(order, event = event))
            log.info("Bitrix24 sync finished: orderId={}, dealId={}, event={}", order.orderId, dealId, event)
        }.onFailure { ex ->
            log.error(
                "Bitrix24 sync failed for orderId={}, dealId={}, event={}: {}",
                order.orderId,
                dealId,
                event,
                ex.message,
            )
        }
    }

    private fun payloadFrom(order: OrderEntity, event: String): Bitrix24DealSyncPayload {
        return Bitrix24DealSyncPayload(
            orderId = order.orderId,
            routeId = order.route.routeId,
            from = order.route.fromCity,
            to = order.route.toCity,
            date = order.route.travelDate.toString(),
            seat = order.seat,
            amount = order.amount,
            status = order.status.name,
            passengerName = order.fullName,
            passengerPassportId = order.passportId,
            bankPaymentId = order.bankPaymentId,
            ticketId = order.ticketId,
            event = event,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(Bitrix24OrderSyncService::class.java)
    }
}
