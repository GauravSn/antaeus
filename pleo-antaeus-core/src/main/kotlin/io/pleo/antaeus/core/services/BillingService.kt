package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class BillingService(
    private val invoiceService: InvoiceService,
    private val paymentProvider: PaymentProvider
) {

    fun processInvoices() {
        invoiceService
            .fetchAll(PENDING)
            .forEach {
                processInvoice(it)
            }
    }

    private fun processInvoice(invoice: Invoice) {
        try {
            logger.info { "Initiating payment for invoice - ${invoice.id}" }
            val isPaid = paymentProvider.charge(invoice)

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
    }

    private fun handleUnpaid(invoice: Invoice) {
        invoiceService.updateStatus(invoice.id, UNPAID)
        logger.info { "Payment for invoice - ${invoice.id} failed, status set to UNPAID." }
    }

    private fun handleFailure(th: Throwable, invoice: Invoice) {
        invoiceService.updateStatus(invoice.id, FAILED)
        logger.error(th) { "Payment for invoice - ${invoice.id} failed." }
    }

    private fun handleNetworkException(e: NetworkException, invoice: Invoice) {
        logger.error(e) { "DevOps team notified about the network issue while invoice - ${invoice.id} billing." }
    }
}
