package com.example.ticket.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class XmlUserDetailsService(
    private val xmlUserStore: XmlUserStore,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val xmlUser = xmlUserStore.findByUsername(username)
            ?: throw UsernameNotFoundException("User '$username' is not configured.")
        val normalizedUsername = xmlUser.username.trim()
        if (normalizedUsername.isEmpty()) {
            throw IllegalStateException("Found user with empty username in XML security users file.")
        }
        if (xmlUser.password.isBlank()) {
            throw IllegalStateException("User '$normalizedUsername' has empty password in XML security users file.")
        }

        val roles = xmlUser.roles.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { AppRole.fromRaw(it) }

        if (roles.isEmpty()) {
            throw IllegalStateException("User '$normalizedUsername' has no roles in XML security users file.")
        }

        val authorities = linkedSetOf<SimpleGrantedAuthority>()
        roles.forEach { role ->
            authorities += SimpleGrantedAuthority("ROLE_${role.name}")
            role.privileges.forEach { privilege ->
                authorities += SimpleGrantedAuthority(privilege.name)
            }
        }

        return User.builder()
            .username(normalizedUsername)
            .password(xmlUser.password.trim())
            .authorities(authorities)
            .build()
    }
}
