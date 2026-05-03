package com.example.ticket.bpm.delegate

import com.example.ticket.bpm.CamundaOrderProcess
import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.repository.OrderRepository
import jakarta.persistence.EntityManager
import org.camunda.bpm.engine.delegate.DelegateExecution
import org.camunda.bpm.engine.delegate.JavaDelegate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration

@Component("syncClientOrderStateDelegate")
class SyncClientOrderStateDelegate(
    private val entityManager: EntityManager,
    private val orderRepository: OrderRepository,
    @Value("\${camunda.client.payment-result-wait-timeout:PT30S}")
    private val waitTimeout: Duration,
    @Value("\${camunda.client.payment-result-poll-interval:PT0.25S}")
    private val pollInterval: Duration,
) : JavaDelegate {

    override fun execute(execution: DelegateExecution) {
        val orderId = BpmVariables.requiredString(execution, CamundaOrderProcess.VAR_ORDER_ID)
        val awaitingPost3dsResolution = BpmVariables.optionalString(execution, CamundaOrderProcess.VAR_3DS_CODE) != null

        val deadlineNanos = System.nanoTime() + waitTimeout.toNanos().coerceAtLeast(0)
        val sleepMillis = pollInterval.toMillis().coerceAtLeast(50)
        var currentStatus: OrderStatus? = null
        var previousStatus: OrderStatus? = null

        while (true) {
            entityManager.clear()
            val order = orderRepository.findById(orderId).orElse(null)
                ?: throw IllegalStateException("Order $orderId not found while syncing client state")

            currentStatus = order.status
            execution.setVariable(CamundaOrderProcess.VAR_CLIENT_ORDER_STATE, currentStatus.name)
            order.ticketId?.let { execution.setVariable(CamundaOrderProcess.VAR_TICKET_ID, it) }
            order.bankPaymentId?.let { execution.setVariable(CamundaOrderProcess.VAR_BANK_PAYMENT_ID, it) }

            if (currentStatus != previousStatus) {
                log.info(
                    "Client process observed server order state: clientProcessInstanceId={}, orderId={}, status={}, bankPaymentId={}, ticketId={}",
                    execution.processInstanceId,
                    orderId,
                    currentStatus.name,
                    order.bankPaymentId,
                    order.ticketId,
                )
                previousStatus = currentStatus
            }

            val shouldStop = if (awaitingPost3dsResolution) {
                currentStatus == OrderStatus.PAID || currentStatus == OrderStatus.DECLINED || currentStatus == OrderStatus.CANCELLED
            } else {
                currentStatus == OrderStatus.PAID || currentStatus == OrderStatus.PENDING_3DS || currentStatus == OrderStatus.DECLINED || currentStatus == OrderStatus.CANCELLED
            }
            if (shouldStop) {
                break
            }

            if (System.nanoTime() >= deadlineNanos) {
                log.warn(
                    "Client process timed out waiting for terminal payment state: clientProcessInstanceId={}, orderId={}, lastStatus={}, waitTimeout={}",
                    execution.processInstanceId,
                    orderId,
                    currentStatus.name,
                    waitTimeout,
                )
                break
            }

            Thread.sleep(sleepMillis)
        }

        log.info(
            "Client process synced server order state: clientProcessInstanceId={}, orderId={}, status={}",
            execution.processInstanceId,
            orderId,
            currentStatus?.name ?: "UNKNOWN",
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncClientOrderStateDelegate::class.java)
    }
}
