package io.pleo.antaeus.core.external

import io.pleo.antaeus.models.BillingCaseEvent

interface BillingCaseHandler {

    //  Create billing cases
    fun handle(event: BillingCaseEvent)
}
