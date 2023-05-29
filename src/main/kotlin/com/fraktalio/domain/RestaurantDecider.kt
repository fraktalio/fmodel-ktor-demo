package com.fraktalio.domain

import com.fraktalio.fmodel.domain.Decider
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.util.stream.Collectors

/**
 * A convenient type alias for Decider<RestaurantCommand?, Restaurant?, RestaurantEvent?>
 */
typealias RestaurantDecider = Decider<RestaurantCommand?, Restaurant?, RestaurantEvent?>

/**
 * Decider is a pure domain component.
 * Decider is a datatype that represents the main decision-making algorithm.
 *
 * `decide / command handlers` is a pure function/lambda that takes any command of type [RestaurantCommand] and input state of type [Restaurant] as parameters, and returns the flow of output events Flow<[RestaurantEvent]> as a result
 * `evolve / event-sourcing handlers` is a pure function/lambda that takes input state of type [Restaurant] and input event of type [RestaurantEvent] as parameters, and returns the output/new state [Restaurant]
 * `initialState` is a starting point / An initial state of [Restaurant]. In our case, it is `null`
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */

fun restaurantDecider() = RestaurantDecider(
    initialState = null,
    decide = { c, s ->
        when (c) {
            is CreateRestaurantCommand ->
                if (s == null) flowOf(RestaurantCreatedEvent(c.identifier, c.name, c.menu))
                else flowOf(
                    RestaurantNotCreatedEvent(
                        c.identifier,
                        c.name,
                        c.menu,
                        Reason("Restaurant already exists"),
                        true
                    )
                )

            is ChangeRestaurantMenuCommand ->
                if (s == null) flowOf(
                    RestaurantMenuNotChangedEvent(
                        c.identifier,
                        c.menu,
                        Reason("Restaurant does not exist"),
                    )
                )
                else flowOf(RestaurantMenuChangedEvent(c.identifier, c.menu))

            is PlaceOrderCommand ->
                if (s == null) flowOf(
                    OrderNotPlacedAtRestaurantEvent(
                        c.identifier,
                        c.lineItems,
                        c.orderIdentifier,
                        Reason("Restaurant does not exist"),
                    )
                )
                else if (!s.isValid(c)) flowOf(
                    OrderRejectedByRestaurantEvent(
                        c.identifier,
                        c.orderIdentifier,
                        Reason("Not on the menu"),
                    )
                )
                else flowOf(
                    OrderPlacedAtRestaurantEvent(c.identifier, c.lineItems, c.orderIdentifier)
                )

            null -> emptyFlow() // We ignore the `null` command by emitting the empty flow. Only the Decider that can handle `null` command can be combined (Monoid) with other Deciders.
        }
    },
    evolve = { s, e ->
        when (e) {
            is RestaurantCreatedEvent -> Restaurant(e.identifier, e.name, e.menu)
            is RestaurantMenuChangedEvent -> s?.copy(menu = e.menu)
            is OrderPlacedAtRestaurantEvent -> s
            is RestaurantErrorEvent -> s // Error events are not changing the state in our/this case.
            null -> s // Null events are not changing the state / We return current state instead. Only the Decider that can handle `null` event can be combined (Monoid) with other Deciders.
        }
    }
)

/**
 * A model of the Restaurant / It represents the state of the Restaurant.
 *
 * @property id A unique identifier
 * @property name A name of the restaurant
 * @property menu Current [RestaurantMenu] of the restaurant
 * @constructor Creates Restaurant
 */
data class Restaurant(
    val id: RestaurantId,
    val name: RestaurantName,
    val menu: RestaurantMenu
)

private fun Restaurant.isValid(command: PlaceOrderCommand): Boolean =
    (menu.menuItems.stream().map { mi -> mi.menuItemId }.collect(Collectors.toList())
        .containsAll(command.lineItems.stream().map { li -> li.menuItemId }.collect(Collectors.toList())))



