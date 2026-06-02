package com.example.bank.service

import com.example.bank.api.BankPayRequest
import com.example.bank.api.Confirm3dsKafkaRequest
import com.example.bank.config.CustomKafkaProperties
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration

@Service
class ConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val kafkaProperties: CustomKafkaProperties,
    private val objectMapper: ObjectMapper,
    transactionManager: PlatformTransactionManager,
    private val bankPaymentService: BankPaymentService,
    private val producerService: ProducerService,
) : DisposableBean {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    companion object {
        private val log = LoggerFactory.getLogger(ConsumerService::class.java)
    }

    @Volatile
    private var running = true
    private val consumerLock = Any()

    @PostConstruct
    fun start() {
        kafkaConsumer.subscribe(
            listOf(
                kafkaProperties.topics.paymentRequest,
                kafkaProperties.topics.retryRequest,
                kafkaProperties.topics.dsRequest,
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
            kafkaProperties.topics.paymentRequest -> handlePaymentRequest(record.value())
            kafkaProperties.topics.retryRequest -> handleRetryRequest(record.value())
            kafkaProperties.topics.dsRequest -> handleDsRequest(record.value())
            else -> log.warn("Неизвестный топик: {}", record.topic())
        }
    }

    private fun handlePaymentRequest(value: String) {
        transactionTemplate.execute {
            val payRequest = objectMapper.readValue<BankPayRequest>(value)
            val response = bankPaymentService.pay(payRequest, payRequest.orderId)
            producerService.send(
                kafkaProperties.topics.paymentResult,
                payRequest.orderId,
                objectMapper.writeValueAsString(response),
            )
        }
    }

    private fun handleRetryRequest(value: String) {
        transactionTemplate.execute {
            val payRequest = objectMapper.readValue<BankPayRequest>(value)
            val response = bankPaymentService.pay(payRequest, payRequest.orderId)
            producerService.send(
                kafkaProperties.topics.retryResponse,
                payRequest.orderId,
                objectMapper.writeValueAsString(response),
            )
        }
    }

    private fun handleDsRequest(value: String) {
        transactionTemplate.execute {
            val confirmRequest = objectMapper.readValue<Confirm3dsKafkaRequest>(value)
            val response = bankPaymentService.confirm3ds(confirmRequest)
            producerService.send(
                kafkaProperties.topics.dsResult,
                confirmRequest.orderId,
                objectMapper.writeValueAsString(response),
            )
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
