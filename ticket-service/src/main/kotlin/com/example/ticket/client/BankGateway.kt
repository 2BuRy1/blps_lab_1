package com.example.ticket.client

import com.example.ticket.api.PayOrderRequest
import com.example.ticket.config.CustomKafkaProperties
import com.example.ticket.service.ProducerService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class BankGateway(
    private val producerService: ProducerService,
    private val objectMapper: ObjectMapper,
    private val customKafkaProperties: CustomKafkaProperties,
) {

    fun authorize(amount: Int, request: PayOrderRequest, orderId: String) {
        val payload = BankPayRequestPayload(
            amount = amount,
            orderId = orderId,
            cardNumber = request.cardNumber,
            expirationDate = request.expirationDate,
            cvv = request.cvv,
        )
        producerService.send(
            customKafkaProperties.topics.paymentRequest,
            orderId,
            objectMapper.writeValueAsString(payload),
        )
    }

    fun retryAuthorize(amount: Int, request: PayOrderRequest, orderId: String) {
        val payload = BankPayRequestPayload(
            amount = amount,
            orderId = orderId,
            cardNumber = request.cardNumber,
            expirationDate = request.expirationDate,
            cvv = request.cvv,
        )
        producerService.send(
            customKafkaProperties.topics.retryRequest,
            orderId,
            objectMapper.writeValueAsString(payload),
        )
    }

    fun confirm3ds(orderId: String, paymentId: String, code: String) {
        val payload = BankConfirm3dsPayload(
            orderId = orderId,
            paymentId = paymentId,
            code = code,
        )
        producerService.send(
            customKafkaProperties.topics.dsRequest,
            orderId,
            objectMapper.writeValueAsString(payload),
        )
    }
}
