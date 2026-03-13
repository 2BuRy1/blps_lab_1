package com.example.bank.security

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "users")
data class XmlUsers(
    @field:JacksonXmlProperty(localName = "user")
    @field:JacksonXmlElementWrapper(useWrapping = false)
    val users: List<XmlUser> = emptyList(),
)

data class XmlUser(
    @field:JacksonXmlProperty(isAttribute = true, localName = "username")
    val username: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "password")
    val password: String = "",

    @field:JacksonXmlProperty(isAttribute = true, localName = "roles")
    val roles: String = "",
)
