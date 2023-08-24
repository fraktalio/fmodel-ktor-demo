package com.fraktalio.application

import com.fraktalio.adapter.publisher.PaymentActionPublisher
import com.fraktalio.domain.Command
import com.fraktalio.domain.Event
import com.fraktalio.domain.PaymentSaga
import com.fraktalio.fmodel.application.SagaManager

typealias PaymentSagaManager = SagaManager<Event?, Command>

/**
 * Saga manager - Integrates the third-party payment provider with the aggregate/our system
 */
internal fun paymentSagaManager(paymentSaga: PaymentSaga, aggregate: Aggregate): PaymentSagaManager = SagaManager(
    paymentSaga,
    PaymentActionPublisher(aggregate)
)