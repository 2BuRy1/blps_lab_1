package com.example.bank.service

import com.example.bank.api.BankPayRequest
import com.example.bank.config.CustomKafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
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
    private val bankPaymentService: BankPaymentService,
    private val producerService: ProducerService
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
                kafkaProperties.topics.paymentRequest,
                kafkaProperties.topics.retryRequest,
                kafkaProperties.topics.dsRequest
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
                        "Получено сообщение: topic={}, offset={}, key={}", record.topic(), record.offset(), record.key()
                    )
                    handleRecord(record)
                }

                if (!records.isEmpty) {
                    kafkaConsumer.commitSync()
                }
            }
        } catch (e: org.apache.kafka.common.errors.WakeupException) {
            log.info("Consumer остановлен")
        } finally {
            kafkaConsumer.close()
        }
    }

    private fun handleRecord(record: ConsumerRecord<String, String>) {
        try {
            when (record.topic()) {
                kafkaProperties.topics.paymentRequest -> handlePaymentRequest(record.value())
                kafkaProperties.topics.dsRequest -> handleDsRequest(record.value())
                else -> log.warn("Неизвестный топик: {}", record.topic())
            }
        } catch (e: Exception) {
            log.error("Ошибка обработки сообщения: topic={}, value={}", record.topic(), record.value(), e)
        }
    }

    private fun handleDsRequest(value: String) {
        print(value)
    }

    private fun handlePaymentRequest(value: String) {
        log.info("Обработка запроса оплаты: {}", value)
        transactionTemplate.execute {
            log.info("TX BEGIN OF PAY")
            val payRequest = objectMapper.readValue<BankPayRequest>(value)
            val orderId = payRequest.orderId
            val response = bankPaymentService.pay(payRequest, orderId)
            log.info("TX PAYMENT STATUS $response")
            producerService.send(kafkaProperties.topics.paymentResult, null, objectMapper.writeValueAsString(response))

        }

    }

    override fun destroy() {
        running = false
        kafkaConsumer.wakeup()
    }
}