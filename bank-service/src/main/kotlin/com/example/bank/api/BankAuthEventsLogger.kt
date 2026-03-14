package com.example.bank.api

import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.stereotype.Component

@Component
class BankAuthEventsLogger {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onSuccess(event: AuthenticationSuccessEvent) {
        val auth = event.authentication
        log.info(
            "BANK_AUTH_RESULT success principal={} authorities={}",
            auth.name,
            auth.authorities.joinToString(",") { it.authority },
        )
    }

    @EventListener
    fun onFailure(event: AbstractAuthenticationFailureEvent) {
        val auth = event.authentication
        log.warn(
            "BANK_AUTH_RESULT failure principal={} reason={}",
            auth?.name ?: "unknown",
            event.exception.message,
        )
    }
}
