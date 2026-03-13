package com.example.ticket.security

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

@Component
class XmlUserStore(
    @Value("\${security.users-xml}")
    private val usersXmlLocation: String,
    @Value("\${security.users-template:classpath:security/users.xml}")
    private val usersTemplateLocation: String,
    private val resourceLoader: ResourceLoader,
) {

    private val xmlMapper = XmlMapper().apply { registerKotlinModule() }
    private val monitor = Any()

    fun findByUsername(username: String): XmlUser? = synchronized(monitor) {
        val normalized = username.trim()
        if (normalized.isEmpty()) return@synchronized null
        readUsersUnsafe().users.firstOrNull { it.username.equals(normalized, ignoreCase = true) }
    }

    fun registerClient(username: String, encodedPassword: String): XmlUser = synchronized(monitor) {
        val normalized = username.trim()
        val users = readUsersUnsafe()

        if (users.users.any { it.username.equals(normalized, ignoreCase = true) }) {
            throw IllegalStateException("User '$normalized' already exists")
        }

        val created = XmlUser(
            username = normalized,
            password = encodedPassword,
            roles = AppRole.CLIENT.name,
        )

        writeUsersUnsafe(
            users.copy(
                users = users.users + created,
            )
        )

        created
    }

    private fun readUsersUnsafe(): XmlUsers {
        ensureStorageInitializedUnsafe()
        val resource = resourceLoader.getResource(usersXmlLocation)
        val parsed = resource.inputStream.use { input ->
            xmlMapper.readValue(input, XmlUsers::class.java)
        }
        return parsed.copy(
            users = parsed.users
                .filter { it.username.isNotBlank() }
                .map {
                    XmlUser(
                        username = it.username.trim(),
                        password = it.password.trim(),
                        roles = it.roles.trim(),
                    )
                },
        )
    }

    private fun writeUsersUnsafe(users: XmlUsers) {
        val filePath = resolveWritableFilePath()
            ?: throw IllegalStateException(
                "security.users-xml must point to a writable file location (for example file:./data/ticket-users.xml)",
            )

        Files.createDirectories(filePath.parent ?: filePath.toAbsolutePath().parent)
        val xml = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(users)
        Files.writeString(
            filePath,
            xml,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun ensureStorageInitializedUnsafe() {
        val filePath = resolveWritableFilePath()
        if (filePath != null) {
            if (Files.exists(filePath)) {
                return
            }

            Files.createDirectories(filePath.parent ?: filePath.toAbsolutePath().parent)
            val template = resourceLoader.getResource(usersTemplateLocation)
            if (template.exists()) {
                template.inputStream.use { input ->
                    Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                writeUsersUnsafe(XmlUsers())
            }
            return
        }

        val resource = resourceLoader.getResource(usersXmlLocation)
        if (!resource.exists()) {
            throw IllegalStateException("Security XML users file not found: $usersXmlLocation")
        }
    }

    private fun resolveWritableFilePath(): Path? {
        if (usersXmlLocation.startsWith("file:")) {
            return Paths.get(URI.create(usersXmlLocation))
        }

        if (!usersXmlLocation.startsWith("classpath:")) {
            return Paths.get(usersXmlLocation)
        }

        return null
    }
}
