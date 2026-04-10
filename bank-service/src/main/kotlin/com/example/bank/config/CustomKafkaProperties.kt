package com.example.bank.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@ConfigurationProperties(prefix = "kafka")
@Component
class CustomKafkaProperties {
     lateinit var bootstrapServers: String
     var producer = Producer()
     var consumer = Consumer()
     var topics = Topics()


}

class Producer {
     lateinit var clientId: String
     var acks = "all"
     var retries = 3
}


class Consumer {
     lateinit var groupId: String
     var autoOffsetReset = "earliest"
     var enableAutoCommit = false
     var pollTimeoutMs: Long = 1000
}

class Topics {
     lateinit var paymentRequest: String
     lateinit var paymentResult: String
     lateinit var dsRequest: String
     lateinit var dsResult: String
     lateinit var retryRequest: String
     lateinit var retryResponse: String

}