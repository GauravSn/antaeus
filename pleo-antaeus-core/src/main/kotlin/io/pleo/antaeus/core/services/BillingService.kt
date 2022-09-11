package io.pleo.antaeus.core.services

import io.github.resilience4j.retry.Retry.decorateCheckedSupplier
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.BillingCaseHandler
import io.pleo.antaeus.core.external.CustomerNotificationProvider
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.BillingCaseCategory
import io.pleo.antaeus.models.BillingCaseCategory.*
import io.pleo.antaeus.models.BillingCaseEvent
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus.*
import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}
private val executor = Executors.newFixedThreadPool(10);

// Local retry on temporary network related errors
private val retryConfig = RetryConfig.custom<Any>()
    .maxAttempts(3)
    .waitDuration(Duration.ofSeconds(1))
    .retryExceptions(NetworkException::class.java)
    .build()
private val retry = RetryRegistry.of(retryConfig).retry("payment-service-retry")

class BillingService(
    private val invoiceService: InvoiceService,
    private val paymentProvider: PaymentProvider,
    private val customerNotificationProvider: CustomerNotificationProvider,
    private val billingCaseHandler: BillingCaseHandler
) {

    fun processInvoices() {
        invoiceService
            .fetchAll(PENDING)
            .forEach {
                executor.submit { processInvoice(it) }
            }
    }

    private fun processInvoice(invoice: Invoice) {
        try {
            val isPaid = decorateCheckedSupplier(retry) {
                logger.info { "Initiating payment for invoice - ${invoice.id}" }
                paymentProvider.charge(invoice)
            }.apply()

            when (isPaid) {
                true -> handleSuccess(invoice)
                false -> handleUnpaid(invoice)
            }
        } catch (e: NetworkException) {
            handleNetworkException(e, invoice)
        } catch (e: Exception) {
            handleFailure(e, invoice)
        }
    }

    private fun handleSuccess(invoice: Invoice) {
        invoiceService.updateStatus(invoice.id, PAID)
        logger.info { "Payment for invoice - ${invoice.id} was successful." }
        customerNotificationProvider.notify(
            invoice.customerId,
            "Your subscription has been renewed."
        )
    }

    private fun handleUnpaid(invoice: Invoice) {
        invoiceService.updateStatus(invoice.id, UNPAID)
        logger.info { "Payment for invoice - ${invoice.id} failed, status set to UNPAID." }
        customerNotificationProvider.notify(
            invoice.customerId,
            "Your subscription could not be renewed since payment was unsuccessful."
        )
    }

    private fun handleFailure(th: Throwable, invoice: Invoice) {
        invoiceService.updateStatus(invoice.id, FAILED)
        logger.error { "Payment for invoice - ${invoice.id} failed." }
        billingCaseHandler.handle(BillingCaseEvent(invoice.id, getBillingCaseCategory(th)))
    }

    private fun handleNetworkException(e: NetworkException, invoice: Invoice) {
        logger.error(e) { "DevOps team notified about the network issue while invoice - ${invoice.id} billing." }
    }

    private fun getBillingCaseCategory(th: Throwable): BillingCaseCategory {
        return when (th) {
            is CustomerNotFoundException -> INVALID_CUSTOMER
            is CurrencyMismatchException -> CURRENCY_MISMATCH
            else -> UNKNOWN
        }
    }
}
