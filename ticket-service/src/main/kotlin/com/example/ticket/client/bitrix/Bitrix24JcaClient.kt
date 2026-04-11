package com.example.ticket.client.bitrix

import com.example.ticket.api.IntegrationUnavailableError
import com.example.ticket.exception.IntegrationUnavailableException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.resource.ResourceException
import jakarta.resource.cci.ConnectionFactory
import jakarta.resource.cci.MappedRecord
import jakarta.resource.cci.Record
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.naming.InitialContext
import javax.naming.NamingException
import java.util.Hashtable

@Component
class Bitrix24JcaClient(
    @Value("\${integration.bitrix.jca.jndi-name}")
    private val jndiName: String,
    @Value("\${integration.bitrix.jca.initial-context-factory:}")
    private val initialContextFactory: String,
    @Value("\${integration.bitrix.jca.provider-url:}")
    private val providerUrl: String,
    @Value("\${integration.bitrix.jca.security-principal:}")
    private val securityPrincipal: String,
    @Value("\${integration.bitrix.jca.security-credentials:}")
    private val securityCredentials: String,
    @Value("\${integration.bitrix.jca.add-operation:crm.deal.add.json}")
    private val addOperation: String,
    @Value("\${integration.bitrix.jca.update-operation:crm.deal.update.json}")
    private val updateOperation: String,
    @Value("\${integration.bitrix.jca.stage.new:NEW}")
    private val newStageId: String,
    @Value("\${integration.bitrix.jca.stage.payment-processing:PREPAYMENT_INVOICE}")
    private val paymentProcessingStageId: String,
    @Value("\${integration.bitrix.jca.stage.paid:WON}")
    private val paidStageId: String,
    @Value("\${integration.bitrix.jca.stage.declined:LOSE}")
    private val declinedStageId: String,
    @Value("\${integration.bitrix.jca.stage.cancelled:LOSE}")
    private val cancelledStageId: String,
    private val objectMapper: ObjectMapper,
) {

    private val connectionFactory: ConnectionFactory by lazy { lookupConnectionFactory() }

    fun createDeal(payload: Bitrix24DealSyncPayload): Long {
        val response = execute(
            operation = addOperation,
            body = Bitrix24DealAddRequest(fields = toDealFields(payload)),
        )

        val dealId = response.path("result").asText().trim()
        if (dealId.isBlank()) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 returned empty deal id for crm.deal.add.",
            )
        }

        return dealId.toLongOrNull()
            ?: throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 returned non-numeric deal id '$dealId'.",
            )
    }

    fun updateDeal(dealId: Long, payload: Bitrix24DealSyncPayload) {
        execute(
            operation = updateOperation,
            body = Bitrix24DealUpdateRequest(id = dealId, fields = toDealFields(payload)),
        )
    }

    private fun execute(operation: String, body: Any): JsonNode {
        val payload = runCatching { objectMapper.writeValueAsString(body) }
            .getOrElse { ex ->
                throw IntegrationUnavailableException(
                    service = IntegrationUnavailableError.Service.BITRIX24,
                    message = "Failed to serialize Bitrix24 payload: ${ex.message}",
                )
            }

        val connection = try {
            connectionFactory.connection
        } catch (ex: ResourceException) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 connector connection error: ${ex.message}",
            )
        }

        val interaction = try {
            connection.createInteraction()
        } catch (ex: ResourceException) {
            runCatching { connection.close() }
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 connector interaction error: ${ex.message}",
            )
        }

        val responseRecord = try {
            interaction.execute(null, requestRecord(operation, payload))
        } catch (ex: ResourceException) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 connector execution error: ${ex.message}",
            )
        } finally {
            runCatching { interaction.close() }
            runCatching { connection.close() }
        }

        val rawResponse = extractResponseBody(responseRecord)

        val json = try {
            objectMapper.readTree(rawResponse)
        } catch (ex: Exception) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 returned invalid JSON: ${ex.message}",
            )
        }

        if (json.has("error")) {
            val error = json.path("error").asText("unknown_error")
            val description = json.path("error_description").asText("no description")
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 error '$error': $description",
            )
        }

        return json
    }

    private fun requestRecord(operation: String, payload: String): MappedRecord<String, Any> {
        val record = Bitrix24MappedRecord(recordName = "bitrixRequest")
        record["operation"] = operation
        record["path"] = operation
        record["httpMethod"] = "POST"
        record["method"] = "POST"
        record["contentType"] = "application/json"
        record["payload"] = payload
        record["body"] = payload
        return record
    }

    private fun extractResponseBody(responseRecord: Record?): String {
        if (responseRecord == null) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 connector returned null response record.",
            )
        }

        if (responseRecord is MappedRecord<*, *>) {
            val value = responseRecord["body"]
                ?: responseRecord["payload"]
                ?: responseRecord["response"]
                ?: responseRecord["result"]

            if (value != null) {
                return if (value is String) value else objectMapper.writeValueAsString(value)
            }
        }

        return objectMapper.writeValueAsString(responseRecord)
    }

    private fun lookupConnectionFactory(): ConnectionFactory {
        val initialContext = try {
            buildInitialContext()
        } catch (ex: NamingException) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "JNDI init failed for Bitrix24 connector: ${ex.message}",
            )
        }

        val attemptedNames = mutableListOf<String>()
        var lookedUp: Any? = null
        var lastNamingException: NamingException? = null

        val failures = mutableListOf<String>()
        for (candidate in buildJndiCandidates()) {
            attemptedNames += candidate
            try {
                lookedUp = initialContext.lookup(candidate)
                break
            } catch (ex: NamingException) {
                lastNamingException = ex
                failures += "$candidate -> ${ex.message}"
            }
        }

        if (lookedUp == null) {
            throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "Bitrix24 ConnectionFactory not found by JNDI candidates ${attemptedNames.joinToString()}. Failures: ${failures.joinToString(" | ")}",
            )
        }

        return lookedUp as? ConnectionFactory
            ?: throw IntegrationUnavailableException(
                service = IntegrationUnavailableError.Service.BITRIX24,
                message = "JNDI '$jndiName' is not a jakarta.resource.cci.ConnectionFactory.",
            )
    }

    private fun buildJndiCandidates(): List<String> {
        val original = jndiName.trim()
        val candidates = linkedSetOf<String>()
        if (original.isBlank()) {
            return emptyList()
        }

        candidates += original

        val withoutJavaPrefix = if (original.startsWith("java:")) {
            original.removePrefix("java:")
        } else {
            original
        }
        candidates += withoutJavaPrefix
        candidates += withoutJavaPrefix.removePrefix("/")

        if (original.startsWith("java:jboss/exported/")) {
            candidates += original.removePrefix("java:jboss/exported/")
        }
        if (original.startsWith("java:/")) {
            candidates += original.removePrefix("java:/")
        }

        if (isRemoteJndi()) {
            val normalized = withoutJavaPrefix.removePrefix("/")
            if (!normalized.startsWith("jboss/exported/")) {
                candidates += "jboss/exported/$normalized"
            }
            if (!original.startsWith("java:jboss/exported/")) {
                candidates += "java:jboss/exported/$normalized"
            }
        }

        return candidates.filter { it.isNotBlank() }
    }

    private fun isRemoteJndi(): Boolean {
        if (jndiName.trim().startsWith("java:/")) {
            return false
        }
        return providerUrl.isNotBlank() || initialContextFactory.isNotBlank()
    }

    private fun buildInitialContext(): InitialContext {
        if (jndiName.trim().startsWith("java:/")) {
            return InitialContext()
        }

        if (providerUrl.isBlank() && initialContextFactory.isBlank()) {
            return InitialContext()
        }

        val env = Hashtable<String, String>()
        if (initialContextFactory.isNotBlank()) {
            env["java.naming.factory.initial"] = initialContextFactory
        }
        if (providerUrl.isNotBlank()) {
            env["java.naming.provider.url"] = providerUrl
        }
        if (securityPrincipal.isNotBlank()) {
            env["java.naming.security.principal"] = securityPrincipal
        }
        if (securityCredentials.isNotBlank()) {
            env["java.naming.security.credentials"] = securityCredentials
        }
        return InitialContext(env)
    }

    private fun toDealFields(payload: Bitrix24DealSyncPayload): Map<String, Any> {
        val comments = buildString {
            appendLine("RZD order sync")
            appendLine("event: ${payload.event}")
            appendLine("orderId: ${payload.orderId}")
            appendLine("routeId: ${payload.routeId}")
            appendLine("from: ${payload.from}")
            appendLine("to: ${payload.to}")
            appendLine("date: ${payload.date}")
            appendLine("seat: ${payload.seat}")
            appendLine("status: ${payload.status}")
            appendLine("passenger: ${payload.passengerName}")
            appendLine("passportId: ${payload.passengerPassportId}")
            appendLine("amount: ${payload.amount}")
            payload.bankPaymentId?.let { appendLine("bankPaymentId: $it") }
            payload.ticketId?.let { appendLine("ticketId: $it") }
        }

        return mapOf(
            "TITLE" to "RZD order ${payload.orderId}: ${payload.from} -> ${payload.to}",
            "STAGE_ID" to mapStatusToStage(payload.status, payload.event),
            "OPPORTUNITY" to payload.amount,
            "CURRENCY_ID" to "RUB",
            "SOURCE_ID" to "WEB",
            "COMMENTS" to comments,
        )
    }

    private fun mapStatusToStage(status: String, event: String): String {
        return when (event) {
            "TICKET_ISSUED" -> paidStageId
            "PAYMENT_DECLINED" -> declinedStageId
            "ORDER_CANCELLED" -> cancelledStageId
            "PAYMENT_RETRY_STARTED",
            "ORDER_REACTIVATED_FOR_RETRY",
            "PAYMENT_STARTED",
            "THREE_DS_REQUIRED",
            "THREE_DS_CONFIRMATION_REQUESTED",
                -> paymentProcessingStageId
            else -> mapStatusToDefaultStage(status)
        }
    }

    private fun mapStatusToDefaultStage(status: String): String {
        return when (status) {
            "CREATED" -> newStageId
            "PAYMENT_PROCESSING", "PENDING_3DS", "CONFIRMING_3DS" -> paymentProcessingStageId
            "PAID" -> paidStageId
            "DECLINED" -> declinedStageId
            "CANCELLED" -> cancelledStageId
            else -> newStageId
        }
    }
}
