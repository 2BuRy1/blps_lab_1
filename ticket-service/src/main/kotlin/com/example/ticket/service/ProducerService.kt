package com.example.ticket.service

import com.atomikos.logging.LoggerFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
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
            log.logInfo(
                "Отправлено → topic=${metadata.topic()}, partition=${metadata.partition()}, offset=${metadata.offset()}, $key={key}",
            )
        } catch (ex: Exception) {
            log.logError("Ошибка отправки в топик $topic ${ex.message}", ex)
            throw IllegalStateException("Kafka send failed for topic=$topic", ex)
        }
    }

    companion object {
        private val log = LoggerFactory.createLogger(ProducerService::class.java)
    }


}
