package com.example.ticket.service

import com.example.ticket.client.BankPayDecision
import com.example.ticket.config.CustomKafkaProperties
import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.repository.OrderRepository
import com.example.ticket.service.integration.Bitrix24OrderSyncService
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
    transactionManager: PlatformTransactionManager,
    private val ticketProcessService: TicketProcessService,
    private val orderRepository: OrderRepository,
    private val bitrix24OrderSyncService: Bitrix24OrderSyncService,
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
                kafkaProperties.topics.dsResult,
            ),
        )

        Thread({ pollLoop() }, "kafka-consumer-thread").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun pollLoop() {
        try {
            while (running) {
                val records = kafkaConsumer.poll(Duration.ofMillis(kafkaProperties.consumer.pollTimeoutMs))
                var batchProcessedSuccessfully = true

                for (record in records) {
                    log.info(
                        "Получено сообщение: topic={}, offset={}, key={}",
                        record.topic(),
                        record.offset(),
                        record.key(),
                    )
                    runCatching { handleRecord(record) }
                        .onFailure { ex ->
                            batchProcessedSuccessfully = false
                            log.error(
                                "Ошибка обработки сообщения: topic={}, value={}",
                                record.topic(),
                                record.value(),
                                ex,
                            )
                        }
                    if (!batchProcessedSuccessfully) {
                        break
                    }
                }

                if (!records.isEmpty && batchProcessedSuccessfully) {
                    kafkaConsumer.commitSync()
                }
            }
        } catch (_: WakeupException) {
            log.info("Consumer остановлен")
        } finally {
            kafkaConsumer.close()
        }
    }

    private fun handleRecord(record: ConsumerRecord<String, String>) {
        when (record.topic()) {
            kafkaProperties.topics.paymentResult -> handleBankResult(record.value(), "payment")
            kafkaProperties.topics.retryResponse -> handleBankResult(record.value(), "retry")
            kafkaProperties.topics.dsResult -> handleBankResult(record.value(), "3ds")
            else -> log.warn("Неизвестный топик: {}", record.topic())
        }
    }

    private fun handleBankResult(value: String, source: String) {
        transactionTemplate.execute {
            val bankResult = objectMapper.readValue<BankPayDecision>(value)
            val order = orderRepository.findByOrderIdForUpdate(bankResult.orderId)
            if (order == null) {
                log.warn("orderId={} не найден для результата source={}", bankResult.orderId, source)
                return@execute
            }

            when (bankResult.status) {
                BankPayDecision.Status.SUCCESS -> {
                    if (order.status != OrderStatus.PAID) {
                        ticketProcessService.issueTicket(order)
                    }
                }

                BankPayDecision.Status.REQUIRES_3DS -> {
                    val paymentId = bankResult.paymentId
                        ?: throw IllegalStateException("Bank response missing payment_id for REQUIRES_3DS")
                    if (order.status != OrderStatus.PAID && order.status != OrderStatus.CANCELLED) {
                        ticketProcessService.markOrderPending3ds(order, paymentId)
                        bitrix24OrderSyncService.syncOrderStateBestEffort(order, event = "THREE_DS_REQUIRED")
                    }
                }

                BankPayDecision.Status.DECLINED -> {
                    if (order.status != OrderStatus.PAID && order.status != OrderStatus.CANCELLED) {
                        ticketProcessService.compensateDeclinedOrder(order)
                    }
                }
            }
        }
    }

    override fun destroy() {
        running = false
        kafkaConsumer.wakeup()
    }
}
