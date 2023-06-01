package com.fraktalio.domain

import com.fraktalio.fmodel.domain.View
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable

/**
 * A convenient type alias for View<RestaurantOrderViewState?,OrderEvent?>
 */
typealias OrderView = View<OrderViewState?, OrderEvent?>

/**
 * Order View is a datatype that represents the event handling algorithm,
 * responsible for translating the events into denormalized state,
 * which is more adequate for querying.
 *
 * `evolve / event handlers` is a pure function/lambda that takes input state of type [OrderViewState] and input event of type [OrderEvent] as parameters, and returns the output/new state [OrderViewState]
 * `initialState` is a starting point / An initial state of [OrderViewState]. In our case, it is `null`
 */
fun orderView() = OrderView(
    initialState = null,
    evolve = { s, e ->
        when (e) {
            is OrderCreatedEvent -> OrderViewState(e.identifier, e.restaurantId, e.status, e.lineItems)
            is OrderPreparedEvent -> s?.copy(status = e.status)
            is OrderRejectedEvent -> s?.copy(status = e.status)
            is OrderErrorEvent -> s // Error events are not changing the state in our/this case.
            null -> s // Null events are not changing the state / We return current state instead. Only the Decider that can handle `null` event can be combined (Monoid) with other Deciders.
        }
    }
)

/**
 * A model of the RestaurantOrderViewState / Projection
 *
 * @property id
 * @property restaurantId
 * @property status
 * @property lineItems
 * @constructor Creates [OrderViewState]
 */
@Serializable
data class OrderViewState(
    val id: OrderId,
    val restaurantId: RestaurantId,
    val status: OrderStatus,
    @Serializable(with = ImmutableListSerializer::class)
    val lineItems: ImmutableList<OrderLineItem>
)
