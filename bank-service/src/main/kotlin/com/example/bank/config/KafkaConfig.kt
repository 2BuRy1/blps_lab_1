package com.example.bank.config

import com.atomikos.logging.LoggerFactory
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*


@Configuration
class KafkaConfig(
    private val kafkaProperties: CustomKafkaProperties,
    private val adminClient: AdminClient,
) {



    @Bean
    fun kafkaConsumer(): KafkaConsumer<String, String> {
        val props = Properties()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        props[ConsumerConfig.GROUP_ID_CONFIG] = kafkaProperties.consumer.groupId
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java.name
        props[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = kafkaProperties.consumer.enableAutoCommit
        props[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = kafkaProperties.consumer.autoOffsetReset
        return KafkaConsumer<String, String>(props)
    }

    @Bean
    fun kafkaProducer(): KafkaProducer<String?, String?> {
        val props = Properties()

        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers


        props[ProducerConfig.CLIENT_ID_CONFIG] = kafkaProperties.producer.clientId


        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name


        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name


        props[ProducerConfig.ACKS_CONFIG] = kafkaProperties.producer.acks


        props[ProducerConfig.RETRIES_CONFIG] = kafkaProperties.producer.retries


        props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        log.logInfo("Kafka producer initialized: servers={${kafkaProperties.bootstrapServers}}")

        return KafkaProducer<String?, String?>(props)
    }

    @PostConstruct
    fun createTopics() {
        val existingTopics = adminClient.listTopics().names().get()
        log.logInfo("Существующие топики: {$existingTopics}")

        val topicsToCreate = listOf(
            kafkaProperties.topics.paymentRequest,
            kafkaProperties.topics.paymentResult,
            kafkaProperties.topics.dsRequest,
            kafkaProperties.topics.dsResult,
            kafkaProperties.topics.retryRequest,
            kafkaProperties.topics.retryResponse
        )
            .filter { it.isNotBlank() }
            .filter { it !in existingTopics }
            .map { topicName ->
                NewTopic(topicName, PARTITIONS, REPLICATION_FACTOR)
            }

        if (topicsToCreate.isEmpty()) {
            log.logInfo("Все топики уже существуют")
            return
        }

        adminClient.createTopics(topicsToCreate).all().get()
        log.logInfo("Созданы топики: {${topicsToCreate.map { it.name() }}")
    }

    companion object {
        private val log = LoggerFactory.createLogger(KafkaConfig::class.java)
        private const val PARTITIONS = 1
        private const val REPLICATION_FACTOR: Short = 1
    }




}