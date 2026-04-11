package com.example.ticket.config

import com.example.ticket.job.CancelExpiredCreatedOrdersJob
import com.example.ticket.job.CancelExpiredPending3dsOrdersJob
import org.quartz.JobDetail
import org.quartz.JobBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.SimpleScheduleBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import java.time.Duration

@Configuration
class QuartzOrderExpirationConfig(
    @Value("\${jobs.order-expiration.scan-interval:2m}")
    private val scanInterval: Duration,
) {

    @Bean
    fun schedulerFactoryBeanCustomizer(
        beanFactory: AutowireCapableBeanFactory,
    ): SchedulerFactoryBeanCustomizer {
        return SchedulerFactoryBeanCustomizer { schedulerFactoryBean ->
            schedulerFactoryBean.setJobFactory(AutowiringSpringBeanJobFactory(beanFactory))
        }
    }

    @Bean
    fun cancelExpiredCreatedOrdersJobDetail(): JobDetail {
        return JobBuilder.newJob(CancelExpiredCreatedOrdersJob::class.java)
            .withIdentity("cancelExpiredCreatedOrdersJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun cancelExpiredCreatedOrdersTrigger(
        cancelExpiredCreatedOrdersJobDetail: JobDetail,
    ): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(cancelExpiredCreatedOrdersJobDetail)
            .withIdentity("cancelExpiredCreatedOrdersTrigger")
            .startNow()
            .withSchedule(repeatingSchedule())
            .build()
    }

    @Bean
    fun cancelExpiredPending3dsOrdersJobDetail(): JobDetail {
        return JobBuilder.newJob(CancelExpiredPending3dsOrdersJob::class.java)
            .withIdentity("cancelExpiredPending3dsOrdersJob")
            .storeDurably()
            .build()
    }

    @Bean
    fun cancelExpiredPending3dsOrdersTrigger(
        cancelExpiredPending3dsOrdersJobDetail: JobDetail,
    ): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(cancelExpiredPending3dsOrdersJobDetail)
            .withIdentity("cancelExpiredPending3dsOrdersTrigger")
            .startNow()
            .withSchedule(repeatingSchedule())
            .build()
    }

    private fun repeatingSchedule(): SimpleScheduleBuilder {
        return SimpleScheduleBuilder.simpleSchedule()
            .withIntervalInMilliseconds(scanInterval.toMillis().coerceAtLeast(1))
            .repeatForever()
    }
}
