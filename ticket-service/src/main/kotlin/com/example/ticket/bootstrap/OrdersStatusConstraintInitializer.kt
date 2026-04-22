package com.example.ticket.bootstrap

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class OrdersStatusConstraintInitializer(
    private val jdbcTemplate: JdbcTemplate,
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(vararg args: String?) {
        runCatching {
            jdbcTemplate.execute(
                """
                ALTER TABLE orders
                DROP CONSTRAINT IF EXISTS orders_status_check
                """.trimIndent()
            )

            jdbcTemplate.update(
                """
                UPDATE orders
                SET status = 'CANCELLED'
                WHERE status = 'CANCELED'
                """.trimIndent()
            )

            jdbcTemplate.update(
                """
                UPDATE orders
                SET status = 'PENDING_3DS'
                WHERE status = 'CONFIRMING_3DS'
                """.trimIndent()
            )

            jdbcTemplate.execute(
                """
                ALTER TABLE orders
                ADD CONSTRAINT orders_status_check
                CHECK (
                    status IN (
                        'CREATED',
                        'PAYMENT_PROCESSING',
                        'PENDING_3DS',
                        'CONFIRMING_3DS',
                        'PAID',
                        'DECLINED',
                        'CANCELLED'
                    )
                )
                """.trimIndent()
            )

            log.info("ORDERS_STATUS_CHECK constraint reconciled to include async statuses")
        }.onFailure { ex ->
            log.warn("ORDERS_STATUS_CHECK reconcile skipped: {}", ex.message)
        }
    }
}
