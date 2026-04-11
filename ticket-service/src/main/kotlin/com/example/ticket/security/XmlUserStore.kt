package com.example.ticket.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Component
class XmlUserStore(
    @Value("\${security.users-xml}")
    private val usersXmlLocation: String,
    @Value("\${security.users-template:classpath:security/users.xml}")
    private val usersTemplateLocation: String,
    private val resourceLoader: ResourceLoader,
) {

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
        val parsed = resource.inputStream.use { input -> parseUsersXml(input) }
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
        Files.newOutputStream(
            filePath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { output ->
            writeUsersXml(users, output)
        }
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
            return runCatching {
                Paths.get(URI.create(usersXmlLocation))
            }.getOrElse {
                // Supports relative form like file:./data/ticket-users.xml
                Paths.get(usersXmlLocation.removePrefix("file:"))
            }
        }

        if (!usersXmlLocation.startsWith("classpath:")) {
            return Paths.get(usersXmlLocation)
        }

        return null
    }

    private fun parseUsersXml(input: java.io.InputStream): XmlUsers {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(input)
        val nodes = document.getElementsByTagName("user")
        val parsedUsers = mutableListOf<XmlUser>()
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val attrs = node.attributes ?: continue
            parsedUsers += XmlUser(
                username = attrs.getNamedItem("username")?.nodeValue ?: "",
                password = attrs.getNamedItem("password")?.nodeValue ?: "",
                roles = attrs.getNamedItem("roles")?.nodeValue ?: "",
            )
        }
        return XmlUsers(parsedUsers)
    }

    private fun writeUsersXml(users: XmlUsers, output: java.io.OutputStream) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.newDocument()
        val root = document.createElement("users")
        document.appendChild(root)

        users.users.forEach { user ->
            val userElement = document.createElement("user")
            userElement.setAttribute("username", user.username)
            userElement.setAttribute("password", user.password)
            userElement.setAttribute("roles", user.roles)
            root.appendChild(userElement)
        }

        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        transformer.transform(DOMSource(document), StreamResult(output))
    }
}
