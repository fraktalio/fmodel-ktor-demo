package com.fraktalio.domain

import com.fraktalio.fmodel.domain.Decider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * A convenient type alias for Decider<OrderCommand?, Order?, OrderEvent?>
 */
typealias OrderDecider = Decider<OrderCommand?, Order?, OrderEvent?>

/**
 * Decider is a pure domain component.
 * Decider is a datatype that represents the main decision-making algorithm.
 *
 * `decide / command handlers` is a pure function/lambda that takes any command of type [OrderCommand] and input state of type [Order] as parameters, and returns the flow of output events Flow<[OrderEvent]> as a result
 * `evolve / event-sourcing handlers` is a pure function/lambda that takes input state of type [Order] and input event of type [OrderEvent] as parameters, and returns the output/new state [Order]
 * `initialState` is a starting point / An initial state of [Order]. In our case, it is `null`
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */
fun orderDecider() = OrderDecider(
    initialState = null,
    decide = { c, s ->
        when (c) {
            is CreateOrderCommand ->
                if (s == null) flowOf(OrderCreatedEvent(c.identifier, c.lineItems, c.restaurantIdentifier))
                else flowOf(OrderRejectedEvent(c.identifier, Reason("Order already exists")))

            is MarkOrderAsPreparedCommand ->
                if (s == null) flowOf(OrderNotPreparedEvent(c.identifier, Reason("Order does not exist")))
                else if (OrderStatus.CREATED != s.status) flowOf(
                    OrderNotPreparedEvent(
                        c.identifier,
                        Reason("Order not in CREATED status"),
                    )
                )
                else flowOf(OrderPreparedEvent(c.identifier))

            null -> emptyFlow() // We ignore the `null` command by emitting the empty flow. Only the Decider that can handle `null` command can be combined (Monoid) with other Deciders.
        }
    },
    evolve = { s, e ->
        when (e) {
            is OrderCreatedEvent -> Order(e.identifier, e.restaurantId, e.status, e.lineItems)
            is OrderPreparedEvent -> s?.copy(status = e.status)
            is OrderRejectedEvent -> s?.copy(status = e.status)
            is OrderErrorEvent -> s // Error events are not changing the state in our/this case.
            null -> s // Null events are not changing the state / We return current state instead. Only the Decider that can handle `null` event can be combined (Monoid) with other Deciders.
        }
    }
)

/**
 * A model of the Order / It represents the state of the Order.
 */
data class Order(
    val id: OrderId,
    val restaurantId: RestaurantId,
    val status: OrderStatus,
    val lineItems: ImmutableList<OrderLineItem>
)
