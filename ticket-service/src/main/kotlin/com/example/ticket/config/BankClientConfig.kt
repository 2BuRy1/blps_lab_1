package com.example.ticket.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient

@Configuration
class BankClientConfig {

    @Bean
    fun bankRestClient(
        @Value("\${integration.bank.base-url}") baseUrl: String,
        @Value("\${integration.bank.username}") username: String,
        @Value("\${integration.bank.password}") password: String,
    ): RestClient {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeaders { headers ->
                headers.setBasicAuth(username, password)
                headers.set(HttpHeaders.ACCEPT, "application/json")
            }
            .build()
    }
}
