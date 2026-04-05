package com.example.bank.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

@Component
class BankAuthDebugFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return !request.requestURI.startsWith("/api/v1/bank/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (authHeader == null) {
            log.warn("BANK_AUTH_IN uri={} method={} authorization=missing", request.requestURI, request.method)
        } else if (authHeader.startsWith("Basic ")) {
            val encoded = authHeader.removePrefix("Basic ").trim()
            val decodedCreds = runCatching {
                val decoded = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
                decoded
            }.getOrNull()
            val username = decodedCreds?.substringBefore(':') ?: "decode_error"
            val passwordLength = decodedCreds?.substringAfter(':', "")?.length ?: -1
            val credsFingerprint = decodedCreds?.let { fingerprint(it) } ?: "n/a"

            log.info(
                "BANK_AUTH_IN uri={} method={} scheme=Basic username={} pass_len={} creds_fp={}",
                request.requestURI,
                request.method,
                username,
                passwordLength,
                credsFingerprint,
            )
        } else {
            log.warn(
                "BANK_AUTH_IN uri={} method={} unsupported_auth_scheme={}",
                request.requestURI,
                request.method,
                authHeader.substringBefore(' '),
            )
        }

        filterChain.doFilter(request, response)
    }

    private fun fingerprint(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(12)
    }
}
