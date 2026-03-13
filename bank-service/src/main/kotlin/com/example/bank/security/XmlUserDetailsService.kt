package com.example.bank.security

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class XmlUserDetailsService(
    @Value("\${security.users-xml:classpath:security/users.xml}")
    private val usersXmlLocation: String,
    private val resourceLoader: ResourceLoader,
) : UserDetailsService {

    private val xmlMapper = XmlMapper().apply { registerKotlinModule() }
    private val usersByUsername: Map<String, UserDetails> by lazy { loadUsers() }

    override fun loadUserByUsername(username: String): UserDetails {
        return usersByUsername[username]
            ?: throw UsernameNotFoundException("User '$username' is not configured.")
    }

    private fun loadUsers(): Map<String, UserDetails> {
        val resource = resourceLoader.getResource(usersXmlLocation)
        if (!resource.exists()) {
            throw IllegalStateException("Security XML users file not found: $usersXmlLocation")
        }

        val xmlUsers = resource.inputStream.use { input ->
            xmlMapper.readValue(input, XmlUsers::class.java)
        }

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

            normalizedUsername to User.builder()
                .username(normalizedUsername)
                .password(xmlUser.password.trim())
                .authorities(authorities)
                .build()
        }
    }
}
