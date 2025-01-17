import io.pleo.antaeus.core.external.CustomerNotificationProvider
import io.pleo.antaeus.core.external.BillingCaseHandler
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.*
import mu.KotlinLogging
import java.math.BigDecimal
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

// This will create all schemas and setup initial data
internal fun setupInitialData(dal: AntaeusDal) {
    val customers = (1..100).mapNotNull {
        dal.createCustomer(
            currency = Currency.values()[Random.nextInt(0, Currency.values().size)]
        )
    }

    customers.forEach { customer ->
        (1..10).forEach {
            dal.createInvoice(
                amount = Money(
                    value = BigDecimal(Random.nextDouble(10.0, 500.0)),
                    currency = customer.currency
                ),
                customer = customer,
                status = if (it == 1) InvoiceStatus.PENDING else InvoiceStatus.PAID
            )
        }
    }
}

// This is the mocked instance of the payment provider
internal fun getPaymentProvider(): PaymentProvider {
    return object : PaymentProvider {
        override fun charge(invoice: Invoice): Boolean {
                return Random.nextBoolean()
        }
    }
}

// Customer notification provider mocked instance
internal fun getCustomerNotificationProvider(): CustomerNotificationProvider {
    return object : CustomerNotificationProvider {
        override fun notify(customerId: Int, message: String) {
            logger.info { "Message - $message sent to customer : $customerId" }
        }
    }
}

// Billing case handler mocked integration
internal fun getBillingCaseHandler(): BillingCaseHandler {
    return object : BillingCaseHandler {
        override fun handle(event: BillingCaseEvent) {
            logger.info { "Event - $event sent to create billing case." }
        }
    }
}
