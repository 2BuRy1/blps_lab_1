package com.example.ticket.service

import com.example.ticket.api.ConflictError
import com.example.ticket.api.LoginResponse
import com.example.ticket.api.RegisterRequest
import com.example.ticket.api.RegisterResponse
import com.example.ticket.api.ValidationDetail
import com.example.ticket.exception.ConflictException
import com.example.ticket.exception.ValidationException
import com.example.ticket.security.XmlUserStore
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val xmlUserStore: XmlUserStore,
    private val passwordEncoder: PasswordEncoder,
) {

    fun register(request: RegisterRequest): RegisterResponse {
        validateRegistrationRequest(request)

        val normalizedUsername = request.username.trim()
        if (xmlUserStore.findByUsername(normalizedUsername) != null) {
            throw ConflictException(
                code = ConflictError.Code.USER_ALREADY_EXISTS,
                message = "User with username '$normalizedUsername' already exists.",
            )
        }

        val encodedPassword = passwordEncoder.encode(request.password)
        val created = try {
            xmlUserStore.registerClient(
                username = normalizedUsername,
                encodedPassword = encodedPassword,
            )
        } catch (ex: IllegalStateException) {
            if (ex.message?.contains("already exists") == true) {
                throw ConflictException(
                    code = ConflictError.Code.USER_ALREADY_EXISTS,
                    message = "User with username '$normalizedUsername' already exists.",
                )
            }
            throw ex
        }

        return RegisterResponse(
            username = created.username,
            roles = listOf("CLIENT"),
        )
    }

    fun login(authentication: Authentication): LoginResponse {
        val authorities = authentication.authorities
            .map { it.authority }
            .sorted()

        val roles = authorities
            .filter { it.startsWith("ROLE_") }
            .map { it.removePrefix("ROLE_") }

        val privileges = authorities
            .filterNot { it.startsWith("ROLE_") }

        return LoginResponse(
            username = authentication.name,
            roles = roles,
            privileges = privileges,
        )
    }

    private fun validateRegistrationRequest(request: RegisterRequest) {
        val details = mutableListOf<ValidationDetail>()
        val usernameRegex = Regex("^[a-zA-Z0-9._-]{3,64}$")

        if (!request.username.matches(usernameRegex)) {
            details += ValidationDetail(
                field = "username",
                issue = "must be 3-64 chars and contain only letters, digits, '.', '_' or '-'",
            )
        }
        if (request.password.length < 8 || request.password.length > 128) {
            details += ValidationDetail(
                field = "password",
                issue = "must be 8-128 characters",
            )
        }
        if (request.password != request.passwordRepeat) {
            details += ValidationDetail(
                field = "password_repeat",
                issue = "must match password",
            )
        }

        if (details.isNotEmpty()) {
            throw ValidationException(details = details)
        }
    }
}
