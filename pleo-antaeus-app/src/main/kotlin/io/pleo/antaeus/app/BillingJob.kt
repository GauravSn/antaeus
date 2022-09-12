package io.pleo.antaeus.app

import io.pleo.antaeus.core.services.BillingService
import mu.KotlinLogging
import org.quartz.Job
import org.quartz.JobExecutionContext

private val logger = KotlinLogging.logger {}

class BillingJob(
    var billingService: BillingService? = null
) : Job {
    override fun execute(context: JobExecutionContext?) {
        logger.info { "Staring pending invoices billing..." }
        billingService?.processInvoices()
    }
}