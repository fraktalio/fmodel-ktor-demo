package com.fraktalio.adapter.persistence

import com.fraktalio.LOGGER
import com.fraktalio.adapter.extension.deciderId
import com.fraktalio.application.MaterializedViewState
import com.fraktalio.application.MaterializedViewStateRepository
import com.fraktalio.domain.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext


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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(10)
    override suspend fun Event?.fetchState(): MaterializedViewState =
        withContext(dbDispatcher) {
            val event = this@fetchState
            LOGGER.debug("view / event-handler: fetchState({}) started ...", event)
            MaterializedViewState(
                event?.let { restaurantRepository.findById(it.deciderId()) },
                event?.let { orderRepository.findById(it.deciderId()) }
            )

        }

    override suspend fun MaterializedViewState.save(): MaterializedViewState =
        withContext(dbDispatcher) {
            LOGGER.debug("view / event-handler: save({}) started ... #########", this@save)
            with(this@save) {
                MaterializedViewState(
                    restaurantRepository.upsertRestaurant(restaurant),
                    orderRepository.upsertOrder(order)
                )
            }
        }
}
