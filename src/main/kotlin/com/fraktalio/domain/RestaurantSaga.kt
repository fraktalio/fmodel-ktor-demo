package com.fraktalio.domain

import com.fraktalio.fmodel.domain.Saga
import kotlinx.coroutines.flow.emptyFlow

/**
 * A convenient type alias for Saga<OrderEvent?, RestaurantCommand>
 */
typealias RestaurantSaga = Saga<OrderEvent?, RestaurantCommand>

/**
 * Saga is a datatype that represents the central point of control deciding what to execute next.
 * It is responsible for mapping different events from aggregates into action results (AR) that the [Saga] then can use to calculate the next actions (A) to be mapped to command of other aggregates.
 *
 * Saga does not maintain the state.
 *
 * `react` is a pure function/lambda that takes any event/action-result of type [OrderEvent] as parameter, and returns the flow of commands/actions Flow<[RestaurantCommand]> to be published further downstream.
 */
fun restaurantSaga() = RestaurantSaga(
    react = { e ->
        when (e) {
            //TODO evolve the example ;), it does not do much at the moment.
            is OrderCreatedEvent -> emptyFlow()
            is OrderPreparedEvent -> emptyFlow()
            is OrderPaidEvent -> emptyFlow()
            is OrderErrorEvent -> emptyFlow()
            null -> emptyFlow() // We ignore the `null` event by returning the empty flow of commands. Only the Saga that can handle `null` event/action-result can be combined (Monoid) with other Sagas.
        }
    }
)
