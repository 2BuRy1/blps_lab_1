package com.example.ticket.service

import com.atomikos.logging.LoggerFactory
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Service

@Service
class ProducerService(
    private val kafkaProducer: KafkaProducer<String?, String?>,
) {
    fun send(topic: String, key: String?, message: String) {
        val record = ProducerRecord(topic, key, message)

        kafkaProducer.send(record) { metadata, exception ->
            if (exception != null) {
                log.logError("Ошибка отправки в топик $topic ${exception.message}", exception)
            } else {
                log.logInfo(
                    "Отправлено → topic=${metadata.topic()}, partition=${metadata.partition()}, offset=${metadata.offset()}, $key={key}",
                )
            }
        }
    }

    companion object {
        private val log = LoggerFactory.createLogger(ProducerService::class.java)
    }


}