package com.example.ticket.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class BankClientConfig {

    @Bean
    fun bankRestClient(
        @Value("\${integration.bank.base-url}") baseUrl: String,
    ): RestClient {
        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")

        require(normalizedBaseUrl.isNotBlank()) { "integration.bank.base-url must not be blank" }

        return RestClient.builder()
            .baseUrl(normalizedBaseUrl)
            .defaultHeaders { headers ->
                headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            }
            .build()
    }
}
