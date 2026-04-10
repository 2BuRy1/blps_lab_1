package com.example.ticket.service

import com.example.ticket.api.PayOrderPending3dsResponse
import com.example.ticket.client.BankPayDecision
import com.example.ticket.config.CustomKafkaProperties
import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.repository.OrderRepository
import com.example.ticket.persistence.repository.TicketRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

@Service
class ConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val kafkaProperties: CustomKafkaProperties,
    private val objectMapper: ObjectMapper,
    val transactionManager: PlatformTransactionManager,
    private val ticketProcessService: TicketProcessService,
    private val orderRepository: OrderRepository,
) : DisposableBean {

    private val transactionTemplate = TransactionTemplate(transactionManager)


    companion object {
        private val log = LoggerFactory.getLogger(ConsumerService::class.java)
    }

    private var running = true

    @PostConstruct
    fun start() {
        kafkaConsumer.subscribe(
            listOf(
                kafkaProperties.topics.paymentResult,
                kafkaProperties.topics.retryResponse,
                kafkaProperties.topics.dsResult
            )
        )


        Thread({
            pollLoop()
        }, "kafka-consumer-thread").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun pollLoop() {
        try {
            while (running) {
                val records = kafkaConsumer.poll(Duration.ofMillis(kafkaProperties.consumer.pollTimeoutMs))

                for (record in records) {
                    log.info(
                        "Получено сообщение: topic={}, offset={}, key={}",
                        record.topic(), record.offset(), record.key()
                    )
                    handleRecord(record)
                }

                if (!records.isEmpty) {
                    kafkaConsumer.commitSync()
                }
            }
        } catch (e: WakeupException) {
            log.info("Consumer остановлен")
        } finally {
            kafkaConsumer.close()
        }
    }

    private fun handleRecord(record: ConsumerRecord<String, String>) {
        try {
            when (record.topic()) {
                kafkaProperties.topics.paymentResult -> handlePaymentResult(record.value())
                kafkaProperties.topics.dsResult -> handleDsResult(record.value())
                else -> log.warn("Неизвестный топик: {}", record.topic())
            }
        } catch (e: Exception) {
            log.error("Ошибка обработки сообщения: topic={}, value={}", record.topic(), record.value(), e)
        }
    }

    fun handlePaymentResult(value: String) {
        transactionTemplate.execute {
            log.info("TX BEGIN OF SENDING RESULT")
            val bankResult = objectMapper.readValue<BankPayDecision>(value)
            val order = orderRepository.findByOrderIdForUpdate(bankResult.orderId)
            requireNotNull(order)
            when (bankResult.status) {

                BankPayDecision.Status.SUCCESS -> ticketProcessService.issueTicket(order)

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
                    ticketProcessService.declineOrder(order, bankResult.reason)
                }
            }
        }
    }


fun handleDsResult(value: String) {
    transactionTemplate.execute {
        log.info("TX END")

    }
}


override fun destroy() {
    running = false
    kafkaConsumer.wakeup()
}
}