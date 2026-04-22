package com.example.ticket.job

import com.example.ticket.service.OrderExpirationService
import org.springframework.beans.factory.annotation.Autowired
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@DisallowConcurrentExecution
class CancelExpiredCreatedOrdersJob : Job {

    @Autowired
    lateinit var orderExpirationService: OrderExpirationService

    override fun execute(context: JobExecutionContext) {
        val cancelledCount = orderExpirationService.cancelExpiredCreatedOrders()
        log.info("Quartz expired CREATED cleanup finished, cancelledCount={}", cancelledCount)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CancelExpiredCreatedOrdersJob::class.java)
    }
}
