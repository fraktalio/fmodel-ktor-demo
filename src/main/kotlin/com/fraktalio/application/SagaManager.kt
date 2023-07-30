package com.fraktalio.application

import com.fraktalio.adapter.publisher.PaymentActionPublisher
import com.fraktalio.domain.Command
import com.fraktalio.domain.Event
import com.fraktalio.domain.PaymentSaga
import com.fraktalio.fmodel.application.SagaManager
import com.fraktalio.fmodel.application.sagaManager

typealias PaymentSagaManager = SagaManager<Event?, Command>


internal fun paymentSagaManager(paymentSaga: PaymentSaga, aggregate: Aggregate): PaymentSagaManager = sagaManager(
    paymentSaga,
    PaymentActionPublisher(aggregate)
)