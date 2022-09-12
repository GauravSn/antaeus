package io.pleo.antaeus.models

data class BillingCaseEvent(
    val invoiceId: Int,
    val category : BillingCaseCategory
)