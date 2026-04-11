package com.example.ticket.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class TimeConfig {

    @Bean
    fun systemClock(): Clock = Clock.systemUTC()
}
