package com.example.bank.api

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.core.io.ResourceLoader
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory

@Service
class XmlUserDetailsService(
    private val resourceLoader: ResourceLoader,
) : UserDetailsService {

    private val log = LoggerFactory.getLogger(javaClass)
    private val usersXmlLocation = "classpath:security/users.xml"
    private val xmlMapper = XmlMapper().apply { registerKotlinModule() }
    private val usersByUsername: Map<String, StoredUser> by lazy { loadUsers() }

    override fun loadUserByUsername(username: String): UserDetails {
        val storedUser = usersByUsername[username]
            ?: throw UsernameNotFoundException("User '$username' is not configured.")
        return User.builder()
            .username(storedUser.username)
            .password(storedUser.password)
            .authorities(storedUser.authorities.map(::SimpleGrantedAuthority))
            .build()
    }

    private fun loadUsers(): Map<String, StoredUser> {
        val resource = resourceLoader.getResource(usersXmlLocation)
        if (!resource.exists()) {
            throw IllegalStateException("Security XML users file not found: $usersXmlLocation")
        }

        val xmlUsers = resource.inputStream.use { input ->
            xmlMapper.readValue(input, XmlUsers::class.java)
        }

        log.info(
            "BANK_USERS_XML loaded from={} users={}",
            usersXmlLocation,
            xmlUsers.users.joinToString(",") { it.username.trim() },
        )

        return xmlUsers.users.associate { xmlUser ->
            val normalizedUsername = xmlUser.username.trim()
            if (normalizedUsername.isEmpty()) {
                throw IllegalStateException("Found user with empty username in $usersXmlLocation")
            }
            if (xmlUser.password.isBlank()) {
                throw IllegalStateException("User '$normalizedUsername' has empty password in $usersXmlLocation")
            }

            val roles = xmlUser.roles.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { AppRole.fromRaw(it) }

            if (roles.isEmpty()) {
                throw IllegalStateException("User '$normalizedUsername' has no roles in $usersXmlLocation")
            }

            val authorities = linkedSetOf<SimpleGrantedAuthority>()
            roles.forEach { role ->
                authorities += SimpleGrantedAuthority("ROLE_${role.name}")
                role.privileges.forEach { privilege ->
                    authorities += SimpleGrantedAuthority(privilege.name)
                }
            }

            normalizedUsername to StoredUser(
                username = normalizedUsername,
                password = normalizePasswordForDelegatingEncoder(xmlUser.password.trim()),
                authorities = authorities.map { it.authority }.toSet(),
            )
        }
    }

    private fun normalizePasswordForDelegatingEncoder(rawPassword: String): String {
        if (rawPassword.isBlank()) {
            return rawPassword
        }

        return if (rawPassword.startsWith("{") && rawPassword.contains("}")) {
            rawPassword
        } else {
            "{noop}$rawPassword"
        }
    }

    private data class StoredUser(
        val username: String,
        val password: String,
        val authorities: Set<String>,
    )
}
