package com.example.ticket.security

data class XmlUsers(
    val users: List<XmlUser> = emptyList(),
)

data class XmlUser(
    val username: String = "",

    val password: String = "",

    val roles: String = "",
)
