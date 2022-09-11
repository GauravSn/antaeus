package io.pleo.antaeus.app

import io.pleo.antaeus.core.services.BillingService
import org.quartz.CronScheduleBuilder.cronSchedule
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

private const val JOB_GROUP = "pleo"
private const val JOB_NAME = "billing-job"
private const val JOB_TRIGGER_NAME = "billing-job-trigger"
private const val JOB_SCHEDULE = "0 0 0 1 * ?"

class BillingJobScheduler(
    private val billingService: BillingService
) {

    fun schedule() {
        // Billing job to initiate pending invoices payment
        val billingJob = JobBuilder
            .newJob()
            .ofType(BillingJob::class.java)
            .withIdentity(JOB_NAME, JOB_GROUP)
            .usingJobData(
                JobDataMap(
                    mapOf("billingService" to billingService)
                )
            ).build()

        // Cron scheduled billing job trigger on first day of the month
        val billingJobTrigger = TriggerBuilder
            .newTrigger()
            .withIdentity(JOB_TRIGGER_NAME, JOB_GROUP)
            .withSchedule(cronSchedule(JOB_SCHEDULE))
            .forJob(JOB_NAME, JOB_GROUP)
            .build()

        val scheduler = StdSchedulerFactory().scheduler
        scheduler.start()
        scheduler.scheduleJob(billingJob, billingJobTrigger)
    }
}