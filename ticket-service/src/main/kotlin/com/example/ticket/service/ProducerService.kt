package com.example.ticket.service

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class ProducerService(
    private val kafkaProducer: KafkaProducer<String?, String?>,
) {
    fun send(topic: String, key: String?, message: String) {
        val record = ProducerRecord(topic, key, message)

        try {
            val metadata = kafkaProducer.send(record).get(10, TimeUnit.SECONDS)
            log.info(
                "Отправлено -> topic={}, partition={}, offset={}, key={}",
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                key,
            )
        } catch (ex: Exception) {
            log.error("Ошибка отправки в топик {}: {}", topic, ex.message, ex)
            throw IllegalStateException("Kafka send failed for topic=$topic", ex)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProducerService::class.java)
    }


}
