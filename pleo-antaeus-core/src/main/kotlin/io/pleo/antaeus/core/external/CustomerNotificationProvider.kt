package io.pleo.antaeus.core.external

interface CustomerNotificationProvider {

    //  To notify customers about any account updates
    fun notify(customerId: Int, message: String)
}
