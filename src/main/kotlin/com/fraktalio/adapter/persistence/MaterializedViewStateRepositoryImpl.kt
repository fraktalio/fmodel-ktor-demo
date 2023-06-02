package com.fraktalio.adapter.persistence

import com.fraktalio.LOGGER
import com.fraktalio.adapter.deciderId
import com.fraktalio.application.MaterializedViewState
import com.fraktalio.application.MaterializedViewStateRepository
import com.fraktalio.domain.Event


/**
 * View repository implementation
 *
 * @constructor Create Materialized View repository impl
 *
 * @author Иван Дугалић / Ivan Dugalic / @idugalic
 */

internal open class MaterializedViewStateRepositoryImpl(
    private val restaurantRepository: RestaurantRepository,
    private val orderRepository: OrderRepository
) :
    MaterializedViewStateRepository {

    override suspend fun Event?.fetchState(): MaterializedViewState {
        LOGGER.debug("view / event-handler: fetchState({}) started ...", this)
        return MaterializedViewState(
            this?.let { restaurantRepository.findById(it.deciderId()) },
            this?.let { orderRepository.findById(it.deciderId()) }
        )

    }

    override suspend fun MaterializedViewState.save(): MaterializedViewState =
        with(this) {
            LOGGER.debug("view / event-handler: save({}) started ... #########", this)
            MaterializedViewState(
                restaurantRepository.upsertRestaurant(restaurant),
                orderRepository.upsertOrder(order)
            )
        }
}
