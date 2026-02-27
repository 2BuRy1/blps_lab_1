package com.example.bank.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private val allowedOriginsRaw: String,
    @Value("\${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private val allowedMethodsRaw: String,
    @Value("\${cors.allowed-headers:*}")
    private val allowedHeadersRaw: String,
    @Value("\${cors.exposed-headers:Location}")
    private val exposedHeadersRaw: String,
    @Value("\${cors.allow-credentials:true}")
    private val allowCredentials: Boolean,
    @Value("\${cors.max-age:3600}")
    private val maxAge: Long,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(*csv(allowedOriginsRaw))
            .allowedMethods(*csv(allowedMethodsRaw))
            .allowedHeaders(*csv(allowedHeadersRaw))
            .exposedHeaders(*csv(exposedHeadersRaw))
            .allowCredentials(allowCredentials)
            .maxAge(maxAge)
    }

    private fun csv(raw: String): Array<String> = raw
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toTypedArray()
}
