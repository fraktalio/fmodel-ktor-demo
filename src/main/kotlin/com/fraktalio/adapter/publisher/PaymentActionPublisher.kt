package com.fraktalio.adapter.publisher

import com.fraktalio.LOGGER
import com.fraktalio.application.Aggregate
import com.fraktalio.domain.Command
import com.fraktalio.domain.MarkOrderAsPayedCommand
import com.fraktalio.domain.PayCommand
import com.fraktalio.fmodel.application.ActionPublisher
import com.fraktalio.fmodel.application.handleOptimistically
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class PaymentActionPublisher(private val aggregate: Aggregate) : ActionPublisher<Command> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun Flow<Command>.publish(): Flow<Command> =
        flow {
            this@publish.collect {
                // use HTTP client to publish the command to the Payment service, lets simulate it with delay.
                LOGGER.info("Publishing command to the Payment service: $it")
                delay(1000)
                // once you get the response from the Payment service, you can emit command back to the internal aggregate
                aggregate.handleOptimistically(MarkOrderAsPayedCommand((it as PayCommand).orderId)).collect()
                // simply emit the successfully published command(s)
                emit(it)
            }
        }
}