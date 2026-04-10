package com.example.ticket.config

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Properties

@Configuration
class KafkaAdminConfig(
    private val customKafkaProperties: CustomKafkaProperties,
) {

    @Bean
    fun kafkaAdminClient(): AdminClient {
        val props = Properties()
        props[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = customKafkaProperties.bootstrapServers
        return AdminClient.create(props)
    }
}