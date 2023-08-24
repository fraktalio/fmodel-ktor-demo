package com.fraktalio.application

import com.fraktalio.domain.*
import com.fraktalio.fmodel.application.MaterializedView
import com.fraktalio.fmodel.application.ViewStateRepository
import com.fraktalio.fmodel.domain.combine

/**
 * A convenient type alias for ViewStateRepository<Event?, MaterializedViewState>
 */
typealias MaterializedViewStateRepository = ViewStateRepository<Event?, MaterializedViewState>

/**
 * A convenient type alias for MaterializedView<MaterializedViewState, Event?>
 */
typealias OrderRestaurantMaterializedView = MaterializedView<MaterializedViewState, Event?>

/**
 * One, big materialized view that is `combining` all views: [restaurantView], [orderView].
 * Every event will be handled by one of the views.
 * The view that is not interested in specific event type will simply ignore it (do nothing).
 *
 * @param restaurantView restaurantView is used internally to handle events and maintain a view state.
 * @param orderView orderView is used internally to handle events and maintain a view state.
 * @param viewStateRepository is used to store the newly produced view state of the Restaurant and/or Restaurant order together
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */
internal fun materializedView(
    restaurantView: RestaurantView,
    orderView: OrderView,
    viewStateRepository: MaterializedViewStateRepository
): OrderRestaurantMaterializedView = MaterializedView(
    // Combining two views into one, and (di)map the inconvenient Pair into a domain specific Data class (MaterializedViewState) that will represent view state better.
    view = restaurantView.combine(orderView).dimapOnState(
        fl = { Pair(it.restaurant, it.order) },
        fr = { MaterializedViewState(it.first, it.second) }
    ),
    viewStateRepository = viewStateRepository
)

/**
 * A domain specific representation of the combined view state.
 */
data class MaterializedViewState(val restaurant: RestaurantViewState?, val order: OrderViewState?)
