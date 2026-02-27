package com.example.ticket.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class BankClientConfig {

    @Bean
    fun bankRestClient(@Value("\${integration.bank.base-url}") baseUrl: String): RestClient {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .build()
    }
}
