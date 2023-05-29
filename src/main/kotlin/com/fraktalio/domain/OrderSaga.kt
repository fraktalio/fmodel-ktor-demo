package com.fraktalio.domain

import com.fraktalio.fmodel.domain.Saga
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A convenient type alias for Saga<RestaurantEvent?, OrderCommand?>
 */
typealias OrderSaga = Saga<RestaurantEvent?, OrderCommand>

/**
 * Saga is a datatype that represents the central point of control deciding what to execute next.
 * It is responsible for mapping different events from aggregates into action results (AR) that the [Saga] then can use to calculate the next actions (A) to be mapped to command of other aggregates.
 *
 * Saga does not maintain the state.
 *
 * `react` is a pure function/lambda that takes any event/action-result of type [RestaurantEvent] as parameter, and returns the flow of commands/actions Flow<[OrderCommand]> to be published further downstream.
 */
fun orderSaga() = OrderSaga(
    react = { e ->
        when (e) {
            is OrderPlacedAtRestaurantEvent -> flowOf(
                CreateOrderCommand(
                    e.orderId,
                    e.identifier,
                    e.lineItems
                )
            )

            is RestaurantCreatedEvent -> emptyFlow()
            is RestaurantMenuChangedEvent -> emptyFlow()
            is RestaurantErrorEvent -> emptyFlow()
            null -> emptyFlow() // We ignore the `null` event by returning the empty flow of commands. Only the Saga that can handle `null` event/action-result can be combined (Monoid) with other Sagas.
        }
    }
)
