package com.fraktalio.domain

import com.fraktalio.fmodel.domain.View
import kotlinx.serialization.Serializable

/**
 * A convenient type alias for View<RestaurantViewState?, RestaurantEvent?>
 */
typealias RestaurantView = View<RestaurantViewState?, RestaurantEvent?>

/**
 * Restaurant View is a datatype that represents the event handling algorithm,
 * responsible for translating the events into denormalized state,
 * which is more adequate for querying.
 *
 * `evolve / event handlers` is a pure function/lambda that takes input state of type [RestaurantViewState] and input event of type [RestaurantEvent] as parameters, and returns the output/new state [RestaurantViewState]
 * `initialState` is a starting point / An initial state of [RestaurantViewState]. In our case, it is `null`
 */
fun restaurantView() = RestaurantView(
    initialState = null,
    evolve = { s, e ->
        when (e) {
            is RestaurantCreatedEvent -> RestaurantViewState(e.identifier, e.name, e.menu)
            is RestaurantMenuChangedEvent -> s?.copy(menu = e.menu)
            is OrderPlacedAtRestaurantEvent -> s
            is RestaurantErrorEvent -> s // Error events are not changing the state in our/this case.
            null -> s // Null events are not changing the state / We return current state instead. Only the Decider that can handle `null` event can be combined (Monoid) with other Deciders.
        }
    }
)

/**
 * A RestaurantViewState / projection
 *
 * @property id A unique identifier
 * @property name A name of the restaurant
 * @property menu Current [RestaurantMenu] of the restaurant
 * @constructor Creates [RestaurantViewState]
 */
@Serializable
data class RestaurantViewState(
    val id: RestaurantId,
    val name: RestaurantName,
    val menu: RestaurantMenu
)