package com.example.ticket.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class BankClientConfig {

    @Bean
    fun bankRestClient(
        @Value("\${integration.bank.base-url}") baseUrl: String,
        @Value("\${integration.bank.connect-timeout:2s}") connectTimeout: Duration,
        @Value("\${integration.bank.read-timeout:5s}") readTimeout: Duration,
    ): RestClient {
        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")

        require(normalizedBaseUrl.isNotBlank()) { "integration.bank.base-url must not be blank" }
        require(!connectTimeout.isZero && !connectTimeout.isNegative) {
            "integration.bank.connect-timeout must be positive"
        }
        require(!readTimeout.isZero && !readTimeout.isNegative) {
            "integration.bank.read-timeout must be positive"
        }

        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            setReadTimeout(readTimeout.toMillis().coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }

        return RestClient.builder()
            .baseUrl(normalizedBaseUrl)
            .requestFactory(requestFactory)
            .defaultHeaders { headers ->
                headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            }
            .build()
    }
}
