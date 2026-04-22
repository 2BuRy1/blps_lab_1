package com.example.ticket.config

import org.quartz.spi.TriggerFiredBundle
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.scheduling.quartz.SpringBeanJobFactory

class AutowiringSpringBeanJobFactory(
    private val beanFactory: AutowireCapableBeanFactory,
) : SpringBeanJobFactory() {

    override fun createJobInstance(bundle: TriggerFiredBundle): Any {
        val job = super.createJobInstance(bundle)
        beanFactory.autowireBean(job)
        return job
    }
}
