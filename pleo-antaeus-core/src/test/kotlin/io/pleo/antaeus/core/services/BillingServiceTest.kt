package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.BillingCaseHandler
import io.pleo.antaeus.core.external.CustomerNotificationProvider
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.BillingCaseCategory.*
import io.pleo.antaeus.models.BillingCaseEvent
import io.pleo.antaeus.models.Currency.EUR
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus.*
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.RuntimeException
import java.math.BigDecimal

class BillingServiceTest {
    private val pendingInvoices = mutableListOf(
        Invoice(1, 1, Money(BigDecimal(100.0), EUR), PENDING)
    )
    private val dal = mockk<AntaeusDal> {
        every { fetchInvoices(PENDING) }.returns(pendingInvoices)
    }
    private val paymentProvider = mockk<PaymentProvider>()
    private val customerNotificationProvider = mockk<CustomerNotificationProvider>()
    private val billingCaseHandler = mockk<BillingCaseHandler>()

    private val billingService = BillingService(
        dal,
        paymentProvider,
        customerNotificationProvider,
        billingCaseHandler
    )

    @BeforeEach
    fun setup() {
        every { customerNotificationProvider.notify(any(), any()) }.returns(Unit)
        every { billingCaseHandler.handle(any()) }.returns(Unit)
    }

    @Test
    fun `must set invoice paid when payment success`() {
        every { dal.updateStatus(1, PAID) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.returns(true)

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            dal.updateStatus(1, PAID)
            customerNotificationProvider.notify(1, "Your subscription has been renewed.")
        }
    }

    @Test
    fun `must set invoice unpaid when payment fails`() {
        every { dal.updateStatus(1, UNPAID) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.returns(false)

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            dal.updateStatus(1, UNPAID)
            customerNotificationProvider.notify(
                1,
                "Your subscription could not be renewed since payment was unsuccessful."
            )
        }
    }

    @Test
    fun `must fail when customer id is not found`() {
        every { dal.updateStatus(1, FAILED) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(CustomerNotFoundException(1))

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            dal.updateStatus(1, FAILED)
            billingCaseHandler.handle(BillingCaseEvent(1, INVALID_CUSTOMER))
        }
    }

    @Test
    fun `must fail when currency mismatched between invoice and customer`() {
        every { dal.updateStatus(1, FAILED) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(CurrencyMismatchException(1, 1))

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            dal.updateStatus(1, FAILED)
            billingCaseHandler.handle(BillingCaseEvent(1, CURRENCY_MISMATCH))
        }
    }

    @Test
    fun `must retry on network exception`() {
        every { dal.updateStatus(1, FAILED) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(NetworkException())

        billingService.processInvoices()

        verify(exactly = 3, timeout = 4000) {
            paymentProvider.charge(pendingInvoices[0])
        }
    }

    @Test
    fun `must be successful after retry`() {
        every { dal.updateStatus(1, PAID) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(NetworkException()).andThen(true)

        billingService.processInvoices()

        verify(exactly = 2, timeout = 2000) {
            paymentProvider.charge(pendingInvoices[0])
        }
        verify(exactly = 1, timeout = 1000) {
            dal.updateStatus(1, PAID)
            customerNotificationProvider.notify(1, "Your subscription has been renewed.")
        }
    }

    @Test
    fun `must fail on unknown exception`() {
        every { dal.updateStatus(1, FAILED) }.returns(Unit)
        every { paymentProvider.charge(pendingInvoices[0]) }.throws(RuntimeException())

        billingService.processInvoices()

        verify(exactly = 1, timeout = 1000) {
            dal.updateStatus(1, FAILED)
            billingCaseHandler.handle(BillingCaseEvent(1, UNKNOWN))
        }
    }
}