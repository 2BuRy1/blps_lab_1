package com.example.bank.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class TransactionManagerProbe {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun txManagerProbeRunner(transactionManager: PlatformTransactionManager): ApplicationRunner {
        return ApplicationRunner {
            log.info("TX_MANAGER active class={}", transactionManager::class.qualifiedName)
        }
    }
}
