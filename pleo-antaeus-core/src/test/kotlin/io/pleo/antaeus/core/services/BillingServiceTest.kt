package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency.EUR
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus.*
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BillingServiceTest {
    private val pendingInvoices = mutableListOf(
        Invoice(1, 1, Money(BigDecimal(100.0), EUR), PENDING)
    )
    private val dal = mockk<AntaeusDal> {
        every { fetchInvoices(PENDING) }.returns(pendingInvoices)
    }
    private val paymentProvider = mockk<PaymentProvider>()

    private val invoiceService = InvoiceService(dal = dal)
    private val billingService = BillingService(
        invoiceService,
        paymentProvider
    )

    @Test
    fun `must set invoice paid when payment success`() {
        every { dal.updateStatus(1, PAID) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.returns(true)

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            invoiceService.updateStatus(1, PAID)
        }
    }

    @Test
    fun `must set invoice unpaid when payment fails`() {
        every { dal.updateStatus(1, UNPAID) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.returns(false)

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            invoiceService.updateStatus(1, UNPAID)
        }
    }

    @Test
    fun `must fail when customer id is not found`() {
        every { dal.updateStatus(1, FAILED) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(CustomerNotFoundException(1))

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            invoiceService.updateStatus(1, FAILED)
        }
    }

    @Test
    fun `must fail when currency mismatched between invoice and customer`() {
        every { dal.updateStatus(1, FAILED) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(CurrencyMismatchException(1, 1))

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            invoiceService.updateStatus(1, FAILED)
        }
    }
}