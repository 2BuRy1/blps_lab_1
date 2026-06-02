package com.example.ticket.service

import com.example.ticket.client.BankPayDecision
import com.example.ticket.config.CustomKafkaProperties
import com.example.ticket.exception.WorkflowCorrelationException
import com.example.ticket.persistence.entity.OrderStatus
import com.example.ticket.persistence.repository.OrderRepository
import com.example.ticket.bpm.CamundaOrderWorkflowService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.WakeupException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val kafkaProperties: CustomKafkaProperties,
    private val objectMapper: ObjectMapper,
    private val orderRepository: OrderRepository,
    private val camundaOrderWorkflowService: CamundaOrderWorkflowService,
) : DisposableBean {

    companion object {
        private val log = LoggerFactory.getLogger(ConsumerService::class.java)
        private val correlationAttempts = 20
        private val correlationRetryDelay = Duration.ofMillis(250)
    }

    @Volatile
    private var running = true
    private val consumerLock = Any()

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
                val records = synchronized(consumerLock) {
                    kafkaConsumer.poll(Duration.ofMillis(kafkaProperties.consumer.pollTimeoutMs))
                }
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
                    synchronized(consumerLock) {
                        kafkaConsumer.commitSync()
                    }
                }
            }
        } catch (_: WakeupException) {
            log.info("Consumer остановлен")
        } finally {
            closeConsumer()
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
        val bankResult = objectMapper.readValue<BankPayDecision>(value)
        log.info(
            "Bank result received: source={}, orderId={}, status={}, paymentId={}",
            source,
            bankResult.orderId,
            bankResult.status,
            bankResult.paymentId,
        )

        repeat(correlationAttempts) { attempt ->
            try {
                camundaOrderWorkflowService.correlateBankDecision(bankResult)
                return
            } catch (ex: WorkflowCorrelationException) {
                if (shouldIgnoreAsAlreadyApplied(bankResult)) {
                    log.warn(
                        "Ignoring duplicate bank result without active wait-state: source={}, orderId={}, status={}",
                        source,
                        bankResult.orderId,
                        bankResult.status,
                    )
                    return
                }
                if (attempt == correlationAttempts - 1) {
                    throw ex
                }
                Thread.sleep(correlationRetryDelay.toMillis())
            }
        }
    }

    private fun shouldIgnoreAsAlreadyApplied(bankResult: BankPayDecision): Boolean {
        val order = orderRepository.findById(bankResult.orderId).orElse(null)
            ?: return true

        return when (bankResult.status) {
            BankPayDecision.Status.SUCCESS -> order.status == OrderStatus.PAID
            BankPayDecision.Status.REQUIRES_3DS -> {
                order.status == OrderStatus.PENDING_3DS ||
                    order.status == OrderStatus.CONFIRMING_3DS ||
                    order.status == OrderStatus.PAID ||
                    order.status == OrderStatus.CANCELLED
            }
            BankPayDecision.Status.DECLINED -> {
                order.status == OrderStatus.DECLINED ||
                    order.status == OrderStatus.CANCELLED
            }
        }
    }

    override fun destroy() {
        running = false
        runCatching {
            synchronized(consumerLock) {
                kafkaConsumer.wakeup()
            }
        }.onFailure { ex ->
            log.warn("Kafka consumer wakeup failed during shutdown: {}", ex.message)
        }
    }

    private fun closeConsumer() {
        runCatching {
            synchronized(consumerLock) {
                kafkaConsumer.close()
            }
        }.onFailure { ex ->
            if (ex is KafkaException || ex is ConcurrentModificationException || ex is IllegalStateException) {
                log.warn("Kafka consumer close failed during shutdown: {}", ex.message)
            } else {
                log.warn("Unexpected Kafka consumer close error during shutdown", ex)
            }
        }
    }
}
